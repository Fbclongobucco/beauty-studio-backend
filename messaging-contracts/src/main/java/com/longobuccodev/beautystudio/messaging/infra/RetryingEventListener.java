package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.application.EventHandler;
import com.longobuccodev.beautystudio.messaging.application.PermanentHandlingException;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.io.IOException;

/**
 * Consumidor com ack manual:
 * <ul>
 *   <li>sucesso: ack;</li>
 *   <li>mensagem malformada ou {@link PermanentHandlingException}: reject sem requeue, que leva a DLQ;</li>
 *   <li>outra falha: copia para a fila de espera {@code <queue>.retry.N}, cujo TTL a devolve a fila principal,
 *       e ack da original; esgotadas as tentativas, DLQ.</li>
 * </ul>
 */
public class RetryingEventListener implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RetryingEventListener.class);

    private final EventHandler<AppointmentEvent> handler;
    private final AppointmentEventCodec codec;
    private final RabbitTemplate template;
    private final MessagingProperties.Consumer config;

    public RetryingEventListener(EventHandler<AppointmentEvent> handler, AppointmentEventCodec codec,
                                 RabbitTemplate template, MessagingProperties.Consumer config) {
        this.handler = handler;
        this.codec = codec;
        this.template = template;
        this.config = config;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        AppointmentEvent event;
        try {
            event = codec.decode(message.getBody()).requireValid();
        } catch (RuntimeException e) {
            log.warn("Mensagem malformada (messageId={}) enviada para a DLQ: {}", messageId, e.getMessage());
            channel.basicReject(tag, false);
            return;
        }

        try {
            handler.handle(event);
            channel.basicAck(tag, false);
        } catch (PermanentHandlingException e) {
            log.error("Evento {} ({}) com falha permanente, enviado para a DLQ: {}", event.eventId(), event.type(),
                    e.getMessage());
            channel.basicReject(tag, false);
        } catch (RuntimeException e) {
            retryOrDeadLetter(message, channel, tag, event, e);
        }
    }

    private void retryOrDeadLetter(Message message, Channel channel, long tag, AppointmentEvent event,
                                   RuntimeException cause) throws IOException {
        int failures = attempts(message.getMessageProperties()) + 1;
        if (failures >= config.maxAttempts()) {
            log.error("Evento {} ({}) falhou {} vezes, enviado para a DLQ: {}", event.eventId(), event.type(),
                    failures, cause.getMessage());
            channel.basicReject(tag, false);
            return;
        }
        int index = config.retryIndex(failures);
        MessageProperties props = message.getMessageProperties();
        props.setHeader(EventContract.ATTEMPTS_HEADER, failures);
        try {
            template.send("", config.retryQueue(index), new Message(message.getBody(), props));
            channel.basicAck(tag, false);
            log.warn("Evento {} ({}) falhou (tentativa {} de {}), nova tentativa em {}: {}", event.eventId(),
                    event.type(), failures, config.maxAttempts(), config.retryDelays().get(index), cause.getMessage());
        } catch (RuntimeException republishFailure) {
            // Sem conseguir agendar a nova tentativa, devolve a mensagem para a fila
            log.warn("Nao foi possivel agendar nova tentativa do evento {}: {}", event.eventId(),
                    republishFailure.getMessage());
            channel.basicNack(tag, false, true);
        }
    }

    static int attempts(MessageProperties props) {
        Object value = props.getHeaders().get(EventContract.ATTEMPTS_HEADER);
        return value instanceof Number n ? n.intValue() : 0;
    }
}
