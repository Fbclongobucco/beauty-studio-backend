package com.longobuccodev.beautystudio.notification.infra;

import com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException;
import com.longobuccodev.beautystudio.notification.core.exception.TransientSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import com.longobuccodev.beautystudio.notification.infra.whatsapp.EvolutionApiSender;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** 11.4: adaptador da Evolution API (contrato conferido no código da v2.3.7). */
class EvolutionApiSenderTest {

    static final String URL = "http://evolution.test/message/sendText/beauty-studio";

    MockRestServiceServer server;
    EvolutionApiSender sender;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://evolution.test")
                .defaultHeader("apikey", "chave-teste");
        server = MockRestServiceServer.bindTo(builder).build();
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(),
                null, new Resilience4JConfigurationProperties());
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(4)
                        .failureRateThreshold(50).waitDurationInOpenState(Duration.ofMinutes(1))
                        .ignoreExceptions(PermanentSendException.class).build())
                .build());
        sender = new EvolutionApiSender(builder.build(), "beauty-studio", factory);
    }

    @Test
    void envioComSucessoDevolveOIdDoProvedor() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "chave-teste"))
                .andExpect(jsonPath("$.number").value("5521999990000"))
                .andExpect(jsonPath("$.text").value("Olá"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"key\":{\"remoteJid\":\"5521999990000@s.whatsapp.net\",\"id\":\"3EB0ABC\"}}"));
        assertThat(sender.send("+5521999990000", "Olá")).isEqualTo(new WhatsAppSender.SendResult("3EB0ABC", false));
        server.verify();
    }

    @Test
    void erro5xxETemporario() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> sender.send("+5521999990000", "Olá")).isInstanceOf(TransientSendException.class);
    }

    @Test
    void numeroInexistenteEPermanente() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"response\":{\"message\":[{\"exists\":false,\"number\":\"5521999990000\"}]}}"));
        assertThatThrownBy(() -> sender.send("+5521999990000", "Olá")).isInstanceOf(PermanentSendException.class)
                .hasMessageContaining("+55219****0000");
    }

    @Test
    void circuitoAbertoNaoChamaOProvedorETemporario() {
        server.expect(times(4), requestTo(URL)).andRespond(withServerError());
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> sender.send("+5521999990000", "Olá")).isInstanceOf(TransientSendException.class);
        }
        server.verify();
        server.reset();
        server.expect(never(), requestTo(URL));
        assertThatThrownBy(() -> sender.send("+5521999990000", "Olá")).isInstanceOf(TransientSendException.class)
                .hasMessageContaining("Circuito aberto");
        server.verify();
    }

    @Test
    void erroPermanenteNaoAbreOCircuito() {
        server.expect(times(5), requestTo(URL)).andRespond(withBadRequest());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> sender.send("+5521999990000", "Olá")).isInstanceOf(PermanentSendException.class);
        }
        server.verify();
    }
}
