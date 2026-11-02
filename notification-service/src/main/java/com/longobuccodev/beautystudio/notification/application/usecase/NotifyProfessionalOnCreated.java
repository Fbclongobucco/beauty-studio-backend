package com.longobuccodev.beautystudio.notification.application.usecase;

import com.longobuccodev.beautystudio.notification.core.domain.AppointmentNotice;
import com.longobuccodev.beautystudio.notification.core.domain.MessageTemplate;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;

import java.util.UUID;

/** Pedido novo: aviso ao WhatsApp que a profissional configurou no próprio painel. */
public class NotifyProfessionalOnCreated {

    static final String NO_PHONE = "Profissional sem WhatsApp cadastrado no painel";

    private final NotificationDelivery delivery;
    private final String panelUrl;

    public NotifyProfessionalOnCreated(NotificationDelivery delivery, String frontUrl) {
        this.delivery = delivery;
        this.panelUrl = (frontUrl.endsWith("/") ? frontUrl.substring(0, frontUrl.length() - 1) : frontUrl) + "/admin";
    }

    public NotificationDelivery.Outcome handle(UUID eventId, String eventType, AppointmentNotice notice) {
        return delivery.deliver(eventId, eventType, RecipientRole.PROFESSIONAL, notice.professionalPhone(),
                MessageTemplate.newRequestForProfessional(notice, panelUrl), NO_PHONE);
    }
}
