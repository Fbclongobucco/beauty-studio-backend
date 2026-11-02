package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.application.PublishFailedException;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEventType;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RabbitEventPublisherIT.App.class)
class RabbitEventPublisherIT extends RabbitTestSupport {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
    }

    @Autowired
    EventPublisher publisher;
    @Autowired
    RabbitTemplate template;
    @Autowired
    AmqpAdmin admin;
    @Autowired
    AppointmentEventCodec codec;

    static AppointmentEvent event(AppointmentEventType type) {
        return AppointmentEvent.of(type, UUID.randomUUID(), OffsetDateTime.now(),
                new AppointmentEvent.Appointment("a1", "pending", LocalDate.parse("2026-11-06"), "11:00",
                        OffsetDateTime.parse("2026-11-06T11:00:00-03:00"),
                        OffsetDateTime.parse("2026-11-06T11:30:00-03:00"), 2000, 30, null, null),
                new AppointmentEvent.Service("manicure", "Manicure"),
                new AppointmentEvent.Professional("stefani", "Stéfani", null),
                new AppointmentEvent.Customer("Maria", "+5521988887777"), "http://front/agendamento/a1");
    }

    @Test
    void publicaComConfirmacaoEPropriedadesDoContrato() {
        Queue queue = new Queue("it.publisher.confirmed", true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(EventContract.APPOINTMENTS_EXCHANGE)).with("appointment.confirmed"));

        AppointmentEvent sent = event(AppointmentEventType.CONFIRMED);
        publisher.publish(sent);

        Message received = template.receive(queue.getName(), 5000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(sent.eventId().toString());
        assertThat(received.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat((Integer) received.getMessageProperties().getHeader(EventContract.VERSION_HEADER))
                .isEqualTo(EventContract.CONTRACT_VERSION);
        assertThat(codec.decode(received.getBody())).isEqualTo(sent);
        admin.deleteQueue(queue.getName());
    }

    @Test
    void eventoSemFilaDeDestinoEFalha() {
        // Nenhuma fila ligada a appointment.rejected neste teste: o broker devolve a mensagem
        assertThatThrownBy(() -> publisher.publish(event(AppointmentEventType.REJECTED)))
                .isInstanceOf(PublishFailedException.class)
                .hasMessageContaining("sem fila de destino");
    }
}
