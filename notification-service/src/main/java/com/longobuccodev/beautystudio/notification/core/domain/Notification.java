package com.longobuccodev.beautystudio.notification.core.domain;

import java.time.Instant;
import java.util.UUID;

/** Registro de uma notificação; único por evento e destinatário (base da idempotência). */
public class Notification {

    private final UUID id;
    private final UUID eventId;
    private final String eventType;
    private final RecipientRole recipientRole;
    private final String recipientPhone;
    private NotificationStatus status;
    private int attempts;
    private String providerMessageId;
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;

    public Notification(UUID id, UUID eventId, String eventType, RecipientRole recipientRole, String recipientPhone,
                        NotificationStatus status, int attempts, String providerMessageId, String lastError,
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

    public static Notification pending(UUID eventId, String eventType, RecipientRole role, String phone, Instant now) {
        return new Notification(UUID.randomUUID(), eventId, eventType, role, phone, NotificationStatus.PENDING, 0,
                null, null, now, now);
    }

    public void sent(String providerId, Instant at) {
        attempts++;
        finish(NotificationStatus.SENT, null, at);
        providerMessageId = providerId;
    }

    public void logged(Instant at) {
        attempts++;
        finish(NotificationStatus.LOGGED, null, at);
    }

    public void skipped(String reason, Instant at) {
        finish(NotificationStatus.SKIPPED, reason, at);
    }

    public void failed(String reason, Instant at) {
        attempts++;
        finish(NotificationStatus.FAILED, reason, at);
    }

    /** Tentativa com falha temporária: continua PENDING para nova tentativa. */
    public void attemptFailed(String reason, Instant at) {
        attempts++;
        lastError = truncate(reason);
        updatedAt = at;
    }

    /** Falha temporária na última tentativa permitida. */
    public void exhausted(String reason, Instant at) {
        finish(NotificationStatus.FAILED, "Tentativas esgotadas: " + reason, at);
    }

    private void finish(NotificationStatus s, String error, Instant at) {
        status = s;
        lastError = truncate(error);
        updatedAt = at;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
    public RecipientRole recipientRole() { return recipientRole; }
    public String recipientPhone() { return recipientPhone; }
    public NotificationStatus status() { return status; }
    public int attempts() { return attempts; }
    public String providerMessageId() { return providerMessageId; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
