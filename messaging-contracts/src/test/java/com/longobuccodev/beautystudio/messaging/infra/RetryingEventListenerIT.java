package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.application.EventHandler;
import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.application.PermanentHandlingException;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEventType;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = RetryingEventListenerIT.App.class, properties = {
        "beauty.messaging.consumer.queue=it.consumer",
        "beauty.messaging.consumer.retry-delays=300ms,600ms",
        "beauty.messaging.consumer.max-attempts=3"})
class RetryingEventListenerIT extends RabbitTestSupport {

    /** Comportamento por eventId: quantas vezes falhar (transitoria) ou falha permanente. */
    static final Map<UUID, Integer> FAIL_TIMES = new ConcurrentHashMap<>();
    static final Set<UUID> PERMANENT = ConcurrentHashMap.newKeySet();
    static final Map<UUID, AtomicInteger> CALLS = new ConcurrentHashMap<>();
    static final List<UUID> HANDLED = new CopyOnWriteArrayList<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean
        EventHandler<AppointmentEvent> handler() {
            return event -> {
                int call = CALLS.computeIfAbsent(event.eventId(), k -> new AtomicInteger()).incrementAndGet();
                if (PERMANENT.contains(event.eventId())) {
                    throw new PermanentHandlingException("permanente", null);
                }
                if (call <= FAIL_TIMES.getOrDefault(event.eventId(), 0)) {
                    throw new IllegalStateException("fora do ar (chamada " + call + ")");
                }
                HANDLED.add(event.eventId());
            };
        }
    }

    @Autowired
    EventPublisher publisher;
    @Autowired
    RabbitTemplate template;

    private AppointmentEvent publish(int failTimes) {
        AppointmentEvent e = RabbitEventPublisherIT.event(AppointmentEventType.CREATED);
        FAIL_TIMES.put(e.eventId(), failTimes);
        publisher.publish(e);
        return e;
    }

    @Test
    void falhaTemporariaERetentadaEDepoisProcessada() {
        AppointmentEvent e = publish(2);
        await().atMost(Duration.ofSeconds(15)).until(() -> HANDLED.contains(e.eventId()));
        assertThat(CALLS.get(e.eventId()).get()).isEqualTo(3);
    }

    @Test
    void esgotadasAsTentativasVaiParaADlq() {
        AppointmentEvent e = publish(99);
        Message dead = await().atMost(Duration.ofSeconds(15))
                .until(() -> template.receive("it.consumer.dlq"), m -> m != null
                        && e.eventId().toString().equals(m.getMessageProperties().getMessageId()));
        assertThat(CALLS.get(e.eventId()).get()).isEqualTo(3);
        assertThat((Integer) dead.getMessageProperties().getHeader(EventContract.ATTEMPTS_HEADER)).isEqualTo(2);
        assertThat(HANDLED).doesNotContain(e.eventId());
    }

    @Test
    void falhaPermanenteVaiDiretoParaADlq() {
        AppointmentEvent e = RabbitEventPublisherIT.event(AppointmentEventType.CREATED);
        PERMANENT.add(e.eventId());
        publisher.publish(e);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> template.receive("it.consumer.dlq"), m -> m != null
                        && e.eventId().toString().equals(m.getMessageProperties().getMessageId()));
        assertThat(CALLS.get(e.eventId()).get()).isEqualTo(1);
    }

    @Test
    void mensagemMalformadaVaiParaADlqSemTravarAFila() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("malformada-1");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        template.send(EventContract.APPOINTMENTS_EXCHANGE, "appointment.created",
                new Message("{\"type\":\"appointment.created\"}".getBytes(StandardCharsets.UTF_8), props));
        AppointmentEvent next = publish(0);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> template.receive("it.consumer.dlq"), m -> m != null
                        && "malformada-1".equals(m.getMessageProperties().getMessageId()));
        await().atMost(Duration.ofSeconds(10)).until(() -> HANDLED.contains(next.eventId()));
    }
}
