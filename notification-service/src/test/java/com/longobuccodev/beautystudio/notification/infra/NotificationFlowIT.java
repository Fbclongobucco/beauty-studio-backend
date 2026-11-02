package com.longobuccodev.beautystudio.notification.infra;

import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEventType;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import com.longobuccodev.beautystudio.notification.core.exception.TransientSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 11.1 e 11.5: serviço sobe, aplica a migration e consome os eventos do RabbitMQ de ponta a ponta. */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "beauty.messaging.consumer.retry-delays=300ms,600ms",
        "beauty.messaging.consumer.max-attempts=5",
        "beauty.notification.front-url=http://front.test",
        "beauty.notification.salon-whatsapp=+5521968117976"})
@Import(NotificationFlowIT.FakeSenderConfig.class)
class NotificationFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management-alpine"));

    /** Provedor de WhatsApp controlado: registra envios e pode falhar nas primeiras N chamadas. */
    static class FakeSender implements WhatsAppSender {
        final List<String[]> sent = new CopyOnWriteArrayList<>();
        final AtomicInteger failNext = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();

        public SendResult send(String phone, String text) {
            calls.incrementAndGet();
            if (failNext.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new TransientSendException("Evolution API fora do ar", null);
            }
            sent.add(new String[]{phone, text});
            return new SendResult("id-" + sent.size(), false);
        }
    }

    @TestConfiguration
    static class FakeSenderConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        FakeSender fakeSender() {
            return new FakeSender();
        }
    }

    @Autowired
    FakeSender sender;
    @Autowired
    EventPublisher publisher;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    RabbitTemplate template;

    @BeforeEach
    void reset() {
        sender.sent.clear();
        sender.failNext.set(0);
        sender.calls.set(0);
        jdbc.sql("delete from notification").update();
    }

    static AppointmentEvent event(AppointmentEventType type, String professionalPhone, String reason) {
        return AppointmentEvent.of(type, UUID.randomUUID(), OffsetDateTime.parse("2026-11-02T08:00:00-03:00"),
                new AppointmentEvent.Appointment("a1", type == AppointmentEventType.CREATED ? "pending"
                        : type.routingKey().substring(12), LocalDate.parse("2026-11-06"), "11:00",
                        OffsetDateTime.parse("2026-11-06T11:00:00-03:00"),
                        OffsetDateTime.parse("2026-11-06T11:30:00-03:00"), 2000, 30, null, reason),
                new AppointmentEvent.Service("manicure", "Manicure"),
                new AppointmentEvent.Professional("stefani", "Stéfani Longo Bucco", professionalPhone),
                new AppointmentEvent.Customer("Maria da Silva", "+5521988887777"),
                "http://front.test/agendamento/a1");
    }

    Map<String, Object> row(UUID eventId) {
        return jdbc.sql("select * from notification where event_id = :id").param("id", eventId).query().singleRow();
    }

    void awaitStatus(UUID eventId, String status) {
        await().atMost(Duration.ofSeconds(20)).until(() -> jdbc.sql(
                        "select count(*) from notification where event_id = :id and status = :s")
                .param("id", eventId).param("s", status).query(Long.class).single() == 1);
    }

    @Test
    void migrationAplicadaNaSubida() {
        assertThat(jdbc.sql("select version from flyway_schema_history where success").query(String.class).list())
                .containsExactly("1");
    }

    @Test
    void pedidoNovoAvisaAProfissionalUmaVezMesmoComEventoDuplicado() {
        AppointmentEvent created = event(AppointmentEventType.CREATED, "+5521999990000", null);
        publisher.publish(created);
        publisher.publish(created);
        awaitStatus(created.eventId(), "SENT");
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> sender.sent.size() == 1);
        assertThat(sender.sent.getFirst()[0]).isEqualTo("+5521999990000");
        assertThat(sender.sent.getFirst()[1]).contains("Maria da Silva").contains("Confirme ou recuse no painel: http://front.test/admin");
        assertThat(row(created.eventId())).containsEntry("recipient_role", "PROFESSIONAL")
                .containsEntry("provider_message_id", "id-1").containsEntry("attempts", 1);
    }

    @Test
    void profissionalSemWhatsappFicaSkipped() {
        AppointmentEvent created = event(AppointmentEventType.CREATED, null, null);
        publisher.publish(created);
        awaitStatus(created.eventId(), "SKIPPED");
        assertThat(sender.calls.get()).isZero();
    }

    @Test
    void cancelamentoAvisaAClienteComMotivoEContato() {
        AppointmentEvent cancelled = event(AppointmentEventType.CANCELLED, "+5521999990000", "Imprevisto de saúde");
        publisher.publish(cancelled);
        awaitStatus(cancelled.eventId(), "SENT");
        assertThat(sender.sent.getFirst()[0]).isEqualTo("+5521988887777");
        assertThat(sender.sent.getFirst()[1]).contains("Cancelado").contains("Motivo: Imprevisto de saúde")
                .contains("(21) 96811-7976");
    }

    @Test
    void provedorForaDoArEDepoisDeVoltaEntregaSemDuplicar() {
        sender.failNext.set(2);
        AppointmentEvent confirmed = event(AppointmentEventType.CONFIRMED, "+5521999990000", null);
        publisher.publish(confirmed);
        awaitStatus(confirmed.eventId(), "SENT");
        assertThat(sender.calls.get()).isEqualTo(3);
        assertThat(sender.sent).hasSize(1);
        assertThat(row(confirmed.eventId())).containsEntry("attempts", 3);
    }

    @Test
    void mensagemMalformadaVaiParaADlqSemTravarAFila() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("malformada-it");
        template.send(EventContract.APPOINTMENTS_EXCHANGE, "appointment.created",
                new Message("{\"sem\":\"eventId\"}".getBytes(StandardCharsets.UTF_8), props));
        AppointmentEvent next = event(AppointmentEventType.CREATED, "+5521999990000", null);
        publisher.publish(next);
        awaitStatus(next.eventId(), "SENT");
        Message dead = await().atMost(Duration.ofSeconds(10))
                .until(() -> template.receive("notification.appointment-events.dlq"), m -> m != null);
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo("malformada-it");
    }

    @Test
    void tentativasEsgotadasFicamFailedENaDlq() {
        sender.failNext.set(99);
        AppointmentEvent created = event(AppointmentEventType.CREATED, "+5521999990000", null);
        publisher.publish(created);
        awaitStatus(created.eventId(), "FAILED");
        assertThat(sender.calls.get()).isEqualTo(5);
        Message dead = await().atMost(Duration.ofSeconds(10))
                .until(() -> template.receive("notification.appointment-events.dlq"), m -> m != null);
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(created.eventId().toString());
    }
}
