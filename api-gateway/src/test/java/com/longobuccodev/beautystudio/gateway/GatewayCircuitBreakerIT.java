package com.longobuccodev.beautystudio.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Booking que nao responde a tempo: fallback 503 e, com o circuito aberto, resposta imediata. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "beauty.gateway.timeout=400ms",
        "beauty.gateway.open-state-wait=60s"})
class GatewayCircuitBreakerIT {

    static final HttpServer STUB = StubServer.start(3000);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri",
                () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    @LocalServerPort
    int port;

    @Test
    void timeoutDaFallbackEDepoisCircuitoAbertoRespondeNaHora() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest slow = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/slow")).build();

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> res = client.send(slow, HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(503);
            assertThat(res.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").contains("\"message\"");
        }

        // Circuito aberto: nem chega ao servico, entao responde bem antes do timeout de 400 ms
        HttpRequest fast = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/services")).build();
        long start = System.nanoTime();
        HttpResponse<String> res = client.send(fast, HttpResponse.BodyHandlers.ofString());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(res.statusCode()).isEqualTo(503);
        assertThat(elapsedMs).isLessThan(300);
    }
}
