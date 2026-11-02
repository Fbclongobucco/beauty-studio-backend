package com.longobuccodev.beautystudio.notification.application.usecase;

import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.notification.core.domain.AppointmentNotice;

/** Encaminha cada evento ao caso de uso certo, a partir dos dados que o próprio evento carrega. */
public class AppointmentEventDispatcher {

    private final NotifyProfessionalOnCreated professional;
    private final NotifyCustomerOnStatusChange customer;

    public AppointmentEventDispatcher(NotifyProfessionalOnCreated professional, NotifyCustomerOnStatusChange customer) {
        this.professional = professional;
        this.customer = customer;
    }

    public NotificationDelivery.Outcome dispatch(AppointmentEvent event) {
        AppointmentNotice notice = toNotice(event);
        return switch (event.eventType()) {
            case CREATED -> professional.handle(event.eventId(), event.type(), notice);
            case CONFIRMED, REJECTED, CANCELLED -> customer.handle(event.eventId(), event.type(), notice);
        };
    }

    static AppointmentNotice toNotice(AppointmentEvent e) {
        return new AppointmentNotice(e.customer().name(), e.customer().phone(), e.professional().name(),
                e.professional().whatsappPhone(), e.service().name(), e.appointment().date(), e.appointment().time(),
                e.appointment().priceCents(), e.appointment().notes(), e.appointment().reason(),
                e.appointment().status(), e.statusUrl());
    }
}
