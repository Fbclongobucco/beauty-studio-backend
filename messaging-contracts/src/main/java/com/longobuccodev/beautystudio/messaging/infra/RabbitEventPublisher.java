package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.application.PublishFailedException;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica na exchange de agendamentos e espera a confirmacao do broker (publisher confirms).
 * Mensagem sem fila de destino (devolvida por {@code mandatory}) tambem e falha: o evento fica para depois.
 */
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate template;
    private final AppointmentEventCodec codec;
    private final Duration confirmTimeout;

    public RabbitEventPublisher(RabbitTemplate template, AppointmentEventCodec codec, Duration confirmTimeout) {
        this.template = template;
        this.codec = codec;
        this.confirmTimeout = confirmTimeout;
        template.setMandatory(true);
    }

    @Override
    public void publish(AppointmentEvent event) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(event.eventId().toString());
        props.setType(event.type());
        props.setHeader(EventContract.VERSION_HEADER, event.version());
        Message message = new Message(codec.encode(event), props);

        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        try {
            template.send(EventContract.APPOINTMENTS_EXCHANGE, event.type(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new PublishFailedException("Broker recusou o evento " + event.eventId() + ": "
                        + confirm.reason(), null);
            }
            if (correlation.getReturned() != null) {
                throw new PublishFailedException("Evento " + event.eventId() + " sem fila de destino ("
                        + correlation.getReturned().getReplyText() + ")", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Publicacao interrompida", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PublishFailedException("Sem confirmacao do broker para o evento " + event.eventId(), e);
        } catch (PublishFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PublishFailedException("Falha ao publicar o evento " + event.eventId(), e);
        }
    }
}
