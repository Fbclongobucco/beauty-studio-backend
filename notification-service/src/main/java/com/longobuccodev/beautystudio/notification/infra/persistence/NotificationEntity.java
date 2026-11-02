package com.longobuccodev.beautystudio.notification.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class NotificationEntity {

    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "recipient_role", nullable = false)
    private String recipientRole;
    @Column(name = "recipient_phone")
    private String recipientPhone;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "provider_message_id")
    private String providerMessageId;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(UUID id, UUID eventId, String eventType, String recipientRole, String recipientPhone,
                              String status, int attempts, String providerMessageId, String lastError,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.recipientRole = recipientRole;
        this.recipientPhone = recipientPhone;
        this.status = status;
        this.attempts = attempts;
        this.providerMessageId = providerMessageId;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getRecipientRole() { return recipientRole; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
