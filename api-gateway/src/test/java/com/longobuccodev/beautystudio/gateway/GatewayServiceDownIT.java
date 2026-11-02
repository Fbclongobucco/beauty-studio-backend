package com.longobuccodev.beautystudio.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Booking parado (porta fechada) ou sem instancia registrada: 503 em JSON, sem detalhe interno. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.client.simple.instances.booking-service[0].uri=http://localhost:1"})
class GatewayServiceDownIT {

    @LocalServerPort
    int port;

    @Test
    void servicoParadoResponde503Padronizado() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/services")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(503);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("json"));
        assertThat(res.body()).isEqualTo("{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":"
                        + "\"Serviço temporariamente indisponível. Tente de novo em instantes.\"}")
                .doesNotContain("localhost");
    }
}
