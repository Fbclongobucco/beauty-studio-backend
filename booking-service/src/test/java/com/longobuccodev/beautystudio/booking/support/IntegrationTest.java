package com.longobuccodev.beautystudio.booking.support;

import com.longobuccodev.beautystudio.booking.infra.config.AccountBootstrap;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: um único contexto Spring e containers únicos (mesmas imagens do compose).
 * Cada teste começa com o catálogo inicial, sem agendamentos, com as contas do ambiente e o relógio em
 * segunda-feira, 02/11/2026, 08:00 em São Paulo.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "beauty.outbox.relay-enabled=false",
        "beauty.messaging.publish-confirm-timeout=2s",
        "beauty.storage.timeout=2s",
        "beauty.security.jwt-secret=segredo-de-teste-com-pelo-menos-32-caracteres!",
        "beauty.admin.username=master",
        "beauty.admin.password=master123",
        "beauty.admin.users=stefani:stefani1;lorrayne:lorrayne1;michele:michele1",
        "beauty.public-urls.front-url=http://front.test",
        "beauty.public-urls.media-url=http://gateway.test",
        "beauty.login.max-failures=5"})
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    public static final Instant MONDAY_8AM = Instant.parse("2026-11-02T11:00:00Z");

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    public static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management-alpine"));
    public static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse("chainguard/minio@sha256:0a01100085e30fb08d2f39e39c9f5c9d83a95707f220b007d813b690cd41622d")
            .asCompatibleSubstituteFor("minio/minio"));

    static {
        POSTGRES.start();
        RABBIT.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("beauty.storage.endpoint", MINIO::getS3URL);
        registry.add("beauty.storage.access-key", MINIO::getUserName);
        registry.add("beauty.storage.secret-key", MINIO::getPassword);
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MONDAY_8AM);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    AccountBootstrap accountBootstrap;
    @Autowired
    com.longobuccodev.beautystudio.booking.infra.security.InMemoryLoginThrottle loginThrottle;

    private static String seedSql;

    @BeforeEach
    void resetData() throws Exception {
        clock.set(MONDAY_8AM);
        loginThrottle.clear();
        if (seedSql == null) {
            // Catálogo inicial (V2) e os ajustes de dados posteriores sobre ele (V4: sem aviso em Unhas)
            seedSql = new ClassPathResource("db/migration/V2__catalogo_inicial.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    + "\n" + new ClassPathResource("db/migration/V4__remove_aviso_unhas.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
        }
        jdbc.sql("truncate appointment_status_history, appointment, outbox_event, account, service, category,"
                + " professional cascade").update();
        jdbc.sql(seedSql).update();
        ApplicationArguments args = new DefaultApplicationArguments();
        accountBootstrap.run(args);
    }

    /** Faz login e devolve o cookie de sessão. */
    protected Cookie login(String username, String password) throws Exception {
        return mvc.perform(post("/api/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("admin_session");
    }

    protected Cookie master() throws Exception {
        return login("master", "master123");
    }

    protected Cookie stefani() throws Exception {
        return login("stefani", "stefani1");
    }

    protected Cookie lorrayne() throws Exception {
        return login("lorrayne", "lorrayne1");
    }

    protected Cookie michele() throws Exception {
        return login("michele", "michele1");
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Cria um pedido pela API pública e devolve o id. */
    protected String book(String serviceId, String date, String time) throws Exception {
        String res = mvc.perform(json(post("/api/appointments"), """
                        {"serviceId":"%s","date":"%s","time":"%s","customerName":"Maria da Silva",
                         "customerPhone":"(21) 98888-7777"}""".formatted(serviceId, date, time)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return res.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
    }

    protected long outboxCount(String type) {
        return jdbc.sql("select count(*) from outbox_event where type = :t").param("t", type).query(Long.class)
                .single();
    }
}
