package com.longobuccodev.beautystudio.notification.application.usecase;

import com.longobuccodev.beautystudio.notification.core.domain.AppointmentNotice;
import com.longobuccodev.beautystudio.notification.core.domain.MessageTemplate;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;

import java.util.UUID;

/** Confirmado, recusado ou cancelado: aviso ao WhatsApp que a cliente informou ao agendar. */
public class NotifyCustomerOnStatusChange {

    private final NotificationDelivery delivery;
    private final String salonWhatsapp;

    public NotifyCustomerOnStatusChange(NotificationDelivery delivery, String salonWhatsapp) {
        this.delivery = delivery;
        this.salonWhatsapp = salonWhatsapp;
    }

    public NotificationDelivery.Outcome handle(UUID eventId, String eventType, AppointmentNotice notice) {
        return delivery.deliver(eventId, eventType, RecipientRole.CUSTOMER, notice.customerPhone(),
                MessageTemplate.statusForCustomer(notice, salonWhatsapp), "Pedido sem telefone da cliente");
    }
}
