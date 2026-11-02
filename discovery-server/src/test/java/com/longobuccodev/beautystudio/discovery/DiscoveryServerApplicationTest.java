package com.longobuccodev.beautystudio.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.thread.Threading;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTest {

    @LocalServerPort
    int port;
    @Autowired
    Environment environment;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthUpEPainelDoEureka() throws Exception {
        assertThat(get("/actuator/health").body()).contains("\"UP\"");
        assertThat(get("/eureka/apps").statusCode()).isEqualTo(200);
        assertThat(Threading.VIRTUAL.isActive(environment)).isTrue();
    }
}
