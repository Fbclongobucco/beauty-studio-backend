package com.longobuccodev.beautystudio.notification.infra.messaging;

import com.longobuccodev.beautystudio.messaging.application.EventHandler;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.notification.application.usecase.AppointmentEventDispatcher;
import com.longobuccodev.beautystudio.notification.application.usecase.NotificationDelivery;
import com.longobuccodev.beautystudio.notification.core.domain.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consumidor dos eventos de agendamento: delega ao dispatcher e registra o resultado com telefone mascarado. */
public class NotificationEventHandler implements EventHandler<AppointmentEvent> {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final AppointmentEventDispatcher dispatcher;

    public NotificationEventHandler(AppointmentEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(AppointmentEvent event) {
        NotificationDelivery.Outcome outcome = dispatcher.dispatch(event);
        if (outcome.duplicate()) {
            log.info("Evento {} ({}) repetido: notificacao para {} ja registrada como {}", event.eventId(),
                    event.type(), outcome.role(), outcome.status());
            return;
        }
        log.info("Notificacao {} evento={} tipo={} destinatario={} telefone={} cliente={}", outcome.status(),
                event.eventId(), event.type(), outcome.role(), Phones.mask(outcome.phone()),
                Phones.maskName(event.customer().name()));
    }
}
