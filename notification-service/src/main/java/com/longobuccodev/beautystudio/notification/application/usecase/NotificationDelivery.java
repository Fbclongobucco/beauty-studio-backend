package com.longobuccodev.beautystudio.notification.application.usecase;

import com.longobuccodev.beautystudio.messaging.application.TransientHandlingException;
import com.longobuccodev.beautystudio.notification.core.domain.Notification;
import com.longobuccodev.beautystudio.notification.core.domain.NotificationStatus;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;
import com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException;
import com.longobuccodev.beautystudio.notification.core.exception.TransientSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import com.longobuccodev.beautystudio.notification.core.repository.NotificationRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrega idempotente de uma mensagem: um registro por evento e destinatário. Evento repetido com registro
 * final não envia de novo. Falha temporária retenta (exceção para a fila); na última tentativa vira FAILED.
 * Erro permanente vira FAILED sem retentar.
 */
public class NotificationDelivery {

    /** Resultado para o log do adaptador (telefone mascarado lá). */
    public record Outcome(UUID eventId, RecipientRole role, String phone, NotificationStatus status,
                          boolean duplicate) {
    }

    private final NotificationRepository repository;
    private final WhatsAppSender sender;
    private final Clock clock;
    private final int maxAttempts;

    public NotificationDelivery(NotificationRepository repository, WhatsAppSender sender, Clock clock,
                                int maxAttempts) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    /** {@code phone} nulo: registro SKIPPED com {@code skipReason}, sem envio nem retentativa. */
    public Outcome deliver(UUID eventId, String eventType, RecipientRole role, String phone, String text,
                           String skipReason) {
        Optional<Notification> existing = repository.find(eventId, role);
        if (existing.isPresent() && existing.get().status().isFinal()) {
            return new Outcome(eventId, role, phone, existing.get().status(), true);
        }
        Notification n = existing.orElseGet(() -> repository
                .insertIfAbsent(Notification.pending(eventId, eventType, role, phone, clock.instant()))
                .orElse(null));
        if (n == null) {
            // Outro consumidor gravou o mesmo evento ao mesmo tempo: ele cuida do envio
            return new Outcome(eventId, role, phone, NotificationStatus.PENDING, true);
        }

        if (phone == null) {
            n.skipped(skipReason, clock.instant());
            repository.update(n);
            return new Outcome(eventId, role, null, n.status(), false);
        }
        try {
            WhatsAppSender.SendResult result = sender.send(phone, text);
            if (result.logOnly()) {
                n.logged(clock.instant());
            } else {
                n.sent(result.providerMessageId(), clock.instant());
            }
            repository.update(n);
        } catch (PermanentSendException e) {
            n.failed(e.getMessage(), clock.instant());
            repository.update(n);
        } catch (TransientSendException e) {
            n.attemptFailed(e.getMessage(), clock.instant());
            if (n.attempts() >= maxAttempts) {
                n.exhausted(e.getMessage(), clock.instant());
            }
            repository.update(n);
            throw new TransientHandlingException("Envio do evento " + eventId + " falhou: " + e.getMessage(), e);
        }
        return new Outcome(eventId, role, phone, n.status(), false);
    }
}
