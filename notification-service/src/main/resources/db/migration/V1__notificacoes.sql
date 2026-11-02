-- Registro das notificacoes por WhatsApp. Unico por evento e destinatario: base da idempotencia
-- (evento repetido nao gera mensagem duplicada).
CREATE TABLE notification (
    id                  UUID PRIMARY KEY,
    event_id            UUID        NOT NULL,
    event_type          TEXT        NOT NULL,
    recipient_role      TEXT        NOT NULL,
    -- E.164; nulo quando nao ha destinatario (ex.: profissional sem WhatsApp)
    recipient_phone     TEXT,
    status              TEXT        NOT NULL,
    attempts            INTEGER     NOT NULL DEFAULT 0,
    provider_message_id TEXT,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT notification_event_recipient_unique UNIQUE (event_id, recipient_role),
    CONSTRAINT notification_role_valid CHECK (recipient_role IN ('PROFESSIONAL', 'CUSTOMER')),
    CONSTRAINT notification_status_valid CHECK (status IN ('PENDING', 'SENT', 'LOGGED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX notification_status_idx ON notification (status, updated_at);
