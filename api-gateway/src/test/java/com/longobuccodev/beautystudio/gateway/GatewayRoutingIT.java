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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "beauty.gateway.cors-allowed-origins=http://localhost:3000"})
class GatewayRoutingIT {

    static final HttpServer STUB = StubServer.start(0);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + STUB.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", () -> base);
        registry.add("beauty.gateway.media-uri", () -> base);
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    @LocalServerPort
    int port;
    final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @Test
    void rotaApiVaiParaOBookingPeloNomeLogico() throws Exception {
        HttpResponse<String> res = send(request("/api/services"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("{\"categories\":[]}");
        assertThat(res.headers().firstValue("X-Path")).contains("/api/services");
    }

    @Test
    void preflightAceitaAOrigemConfiguradaComCredenciais() throws Exception {
        HttpResponse<String> res = send(request("/api/admin/login")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
        assertThat(res.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
    }

    @Test
    void preflightNaoAutorizaOutraOrigem() throws Exception {
        HttpResponse<String> res = send(request("/api/admin/login")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"));
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void fotoPublicaServidaSemOPrefixoMedia() throws Exception {
        HttpResponse<String> res = send(request("/media/service-photos/services/manicure/a.jpg"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(res.headers().firstValue("X-Path")).contains("/service-photos/services/manicure/a.jpg");
    }

    @Test
    void gatewayNaoGuardaCookiesEntreClientes() throws Exception {
        // Resposta do booking com a sessão de uma pessoa...
        HttpResponse<String> login = send(request("/api/set-cookie"));
        assertThat(login.headers().firstValue("Set-Cookie")).hasValueSatisfying(v -> assertThat(v).contains("sessao-da-maria"));
        // ...não pode ser reenviada pelo gateway na requisição de outra pessoa, sem cookie
        HttpResponse<String> other = send(request("/api/echo-cookie"));
        assertThat(other.body()).isEqualTo("null");
        // O cookie do próprio navegador continua sendo repassado
        HttpResponse<String> own = send(request("/api/echo-cookie").header("Cookie", "admin_session=minha"));
        assertThat(own.body()).isEqualTo("admin_session=minha");
    }

    @Test
    void gatewayAcrescentaOIpDoClienteNoFimDoXForwardedFor() throws Exception {
        assertThat(send(request("/api/echo-forwarded")).body()).isEqualTo("127.0.0.1");
        // Valor enviado pelo cliente fica antes; o último é sempre o que o gateway viu
        assertThat(send(request("/api/echo-forwarded").header("X-Forwarded-For", "1.2.3.4")).body())
                .isEqualTo("1.2.3.4,127.0.0.1");
    }

    @Test
    void escritaEmMediaERecusada() throws Exception {
        HttpResponse<String> res = send(request("/media/service-photos/x.jpg")
                .PUT(HttpRequest.BodyPublishers.ofString("x")));
        assertThat(res.statusCode()).isIn(404, 405);
    }
}
