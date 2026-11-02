package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.infra.messaging.OutboxRelay;
import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import com.longobuccodev.beautystudio.messaging.infra.AppointmentEventCodec;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seção 9: evento completo no outbox e publicação confiável pelo relay. */
class OutboxIT extends IntegrationTest {

    static final String QUEUE = "it.booking.outbox";

    @Autowired
    OutboxRelay relay;
    @Autowired
    AppointmentEventCodec codec;
    @Autowired
    AmqpAdmin admin;
    @Autowired
    RabbitTemplate template;

    @BeforeEach
    void queue() {
        Queue q = new Queue(QUEUE, true, false, false);
        admin.declareQueue(q);
        admin.declareBinding(BindingBuilder.bind(q).to(new TopicExchange(EventContract.APPOINTMENTS_EXCHANGE))
                .with(EventContract.ALL_APPOINTMENT_EVENTS));
        admin.purgeQueue(QUEUE, true);
    }

    @AfterEach
    void deleteQueue() {
        admin.deleteQueue(QUEUE);
    }

    List<AppointmentEvent> outbox() {
        return jdbc.sql("select payload::text from outbox_event order by seq").query(String.class).list().stream()
                .map(p -> codec.decode(p.getBytes(StandardCharsets.UTF_8))).toList();
    }

    void act(Cookie session, String id, String action, String body) throws Exception {
        mvc.perform(json(post("/api/admin/appointments/" + id + "/" + action).cookie(session), body))
                .andExpect(status().isOk());
    }

    @Test
    void payloadCompletoParaOsQuatroTipos() throws Exception {
        Cookie stefani = stefani();
        mvc.perform(json(patch("/api/admin/me").cookie(stefani), "{\"whatsappPhone\":\"(21) 99999-0000\"}"));
        String a = book("manicure", "2026-11-06", "11:00");
        act(stefani, a, "confirm", "{}");
        act(stefani, a, "cancel", "{\"reason\":\"Imprevisto de saúde\"}");
        String b = book("pedicure", "2026-11-06", "14:00");
        act(stefani, b, "reject", "{\"reason\":\"Agenda cheia\"}");

        List<AppointmentEvent> events = outbox();
        assertThat(events).extracting(AppointmentEvent::type).containsExactly("appointment.created",
                "appointment.confirmed", "appointment.cancelled", "appointment.created", "appointment.rejected");
        events.forEach(AppointmentEvent::requireValid);
        AppointmentEvent cancelled = events.get(2);
        assertThat(cancelled.version()).isEqualTo(EventContract.CONTRACT_VERSION);
        assertThat(cancelled.appointment().status()).isEqualTo("cancelled");
        assertThat(cancelled.appointment().reason()).isEqualTo("Imprevisto de saúde");
        assertThat(cancelled.appointment().start().toString()).isEqualTo("2026-11-06T11:00-03:00");
        assertThat(cancelled.appointment().priceCents()).isEqualTo(2000);
        assertThat(cancelled.service()).isEqualTo(new AppointmentEvent.Service("manicure", "Manicure"));
        assertThat(cancelled.professional()).isEqualTo(
                new AppointmentEvent.Professional("stefani", "Stéfani Longo Bucco", "+5521999990000"));
        assertThat(cancelled.customer()).isEqualTo(new AppointmentEvent.Customer("Maria da Silva", "+5521988887777"));
        assertThat(cancelled.statusUrl()).isEqualTo("http://front.test/agendamento/" + a);
        assertThat(events.get(4).appointment().reason()).isEqualTo("Agenda cheia");
        assertThat(events.stream().map(AppointmentEvent::eventId).collect(Collectors.toSet())).hasSize(5);
    }

    @Test
    void relayPublicaEmOrdemComConfirmacaoEMarcaComoPublicado() throws Exception {
        String a = book("manicure", "2026-11-06", "11:00");
        act(stefani(), a, "confirm", "{}");
        assertThat(relay.relayOnce()).isEqualTo(2);
        assertThat(relay.pendingCount()).isZero();
        Message first = template.receive(QUEUE, 5000);
        Message second = template.receive(QUEUE, 5000);
        assertThat(codec.decode(first.getBody()).type()).isEqualTo("appointment.created");
        assertThat(codec.decode(second.getBody()).type()).isEqualTo("appointment.confirmed");
        assertThat(relay.relayOnce()).isZero();
    }

    @Test
    void brokerForaDoArNaoImpedeAOperacaoEOEventoSaiQuandoEleVolta() throws Exception {
        RABBIT.execInContainer("rabbitmqctl", "stop_app");
        try {
            String id = book("manicure", "2026-11-06", "11:00");
            assertThat(id).isNotBlank();
            assertThat(relay.relayOnce()).isZero();
            assertThat(relay.pendingCount()).isEqualTo(1);
            Map<String, Object> row = jdbc.sql("select attempts, last_error from outbox_event").query().singleRow();
            assertThat((Integer) row.get("attempts")).isEqualTo(1);
            assertThat(row.get("last_error")).isNotNull();
        } finally {
            RABBIT.execInContainer("rabbitmqctl", "start_app");
        }
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
                .until(() -> relay.relayOnce() == 1);
        assertThat(relay.pendingCount()).isZero();
        assertThat(template.receive(QUEUE, 5000)).isNotNull();
    }

    @Test
    void limpezaRemoveSoPublicadosAntigos() throws Exception {
        book("manicure", "2026-11-06", "11:00");
        relay.relayOnce();
        book("manicure", "2026-11-06", "12:00");
        clock.advance(Duration.ofDays(8));
        assertThat(relay.cleanup()).isEqualTo(1);
        assertThat(relay.pendingCount()).isEqualTo(1);
    }
}
