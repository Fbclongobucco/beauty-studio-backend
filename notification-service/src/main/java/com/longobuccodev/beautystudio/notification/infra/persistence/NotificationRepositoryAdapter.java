package com.longobuccodev.beautystudio.notification.infra.persistence;

import com.longobuccodev.beautystudio.notification.core.domain.Notification;
import com.longobuccodev.beautystudio.notification.core.domain.NotificationStatus;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;
import com.longobuccodev.beautystudio.notification.core.repository.NotificationRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;
    private final JdbcClient jdbc;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Notification> find(UUID eventId, RecipientRole role) {
        return jpa.findByEventIdAndRecipientRole(eventId, role.name()).map(NotificationRepositoryAdapter::toDomain);
    }

    /** {@code ON CONFLICT DO NOTHING} na chave (evento, destinatário): só um consumidor fica com o envio. */
    @Override
    public Optional<Notification> insertIfAbsent(Notification n) {
        int inserted = jdbc.sql("""
                        insert into notification (id, event_id, event_type, recipient_role, recipient_phone, status,
                          attempts, created_at, updated_at)
                        values (:id, :eventId, :type, :role, :phone, :status, :attempts, :createdAt, :updatedAt)
                        on conflict (event_id, recipient_role) do nothing""")
                .param("id", n.id()).param("eventId", n.eventId()).param("type", n.eventType())
                .param("role", n.recipientRole().name()).param("phone", n.recipientPhone())
                .param("status", n.status().name()).param("attempts", n.attempts())
                .param("createdAt", Timestamp.from(n.createdAt())).param("updatedAt", Timestamp.from(n.updatedAt()))
                .update();
        return inserted == 1 ? Optional.of(n) : Optional.empty();
    }

    @Override
    public Notification update(Notification n) {
        return toDomain(jpa.save(new NotificationEntity(n.id(), n.eventId(), n.eventType(), n.recipientRole().name(),
                n.recipientPhone(), n.status().name(), n.attempts(), n.providerMessageId(), n.lastError(),
                n.createdAt(), n.updatedAt())));
    }

    static Notification toDomain(NotificationEntity e) {
        return new Notification(e.getId(), e.getEventId(), e.getEventType(), RecipientRole.valueOf(e.getRecipientRole()),
                e.getRecipientPhone(), NotificationStatus.valueOf(e.getStatus()), e.getAttempts(),
                e.getProviderMessageId(), e.getLastError(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
