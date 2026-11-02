package com.longobuccodev.beautystudio.notification.core.domain;

/**
 * PENDING: em tentativa. SENT: enviada ao provedor. LOGGED: só registrada no log (envio real desligado).
 * SKIPPED: sem destinatário (ex.: profissional sem WhatsApp). FAILED: erro permanente ou tentativas esgotadas.
 */
public enum NotificationStatus {
    PENDING, SENT, LOGGED, SKIPPED, FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
