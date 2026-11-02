-- Esquema inicial do booking-service. Hibernate nunca cria nem altera tabelas (ddl-auto=none).

-- Exclusion constraint de confirmados sobrepostos. Extensao confiavel: o dono do banco pode criar.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE professional (
    id             TEXT PRIMARY KEY,
    name           TEXT        NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    -- E.164, informado pela propria profissional no painel
    whatsapp_phone TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT professional_whatsapp_e164 CHECK (whatsapp_phone IS NULL OR whatsapp_phone ~ '^\+55[0-9]{10,11}$')
);

CREATE TABLE category (
    id         TEXT PRIMARY KEY,
    name       TEXT    NOT NULL,
    note       TEXT,
    sort_order INTEGER NOT NULL
);

CREATE TABLE service (
    id                 TEXT PRIMARY KEY,
    name               TEXT    NOT NULL,
    category_id        TEXT    NOT NULL REFERENCES category (id),
    subcategory        TEXT,
    duration_min       INTEGER NOT NULL,
    -- Duracao ESTIMATIVA ainda nao revisada pelo salao
    duration_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    -- Centavos; nulo = "Consultar valor"
    price_cents        INTEGER,
    professional_id    TEXT    NOT NULL REFERENCES professional (id),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order         INTEGER NOT NULL,
    CONSTRAINT service_duration_grid CHECK (duration_min BETWEEN 30 AND 480 AND duration_min % 30 = 0),
    CONSTRAINT service_price_non_negative CHECK (price_cents IS NULL OR price_cents >= 0)
);

CREATE TABLE appointment (
    id                UUID PRIMARY KEY,
    service_id        TEXT        NOT NULL REFERENCES service (id),
    professional_id   TEXT        NOT NULL REFERENCES professional (id),
    -- Copias do servico no momento do pedido
    service_name      TEXT        NOT NULL,
    professional_name TEXT        NOT NULL,
    duration_min      INTEGER     NOT NULL,
    price_cents       INTEGER,
    date              DATE        NOT NULL,
    time              TIME        NOT NULL,
    starts_at         TIMESTAMPTZ NOT NULL,
    ends_at           TIMESTAMPTZ NOT NULL,
    customer_name     TEXT        NOT NULL,
    customer_phone    TEXT        NOT NULL,
    notes             TEXT,
    status            TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    confirmed_at      TIMESTAMPTZ,
    rejected_at       TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    reason            TEXT,
    seen_at           TIMESTAMPTZ,
    CONSTRAINT appointment_status_valid CHECK (status IN ('pending', 'confirmed', 'rejected', 'cancelled')),
    CONSTRAINT appointment_interval CHECK (ends_at > starts_at),
    -- Dois confirmados da mesma profissional nunca se sobrepoem, mesmo com confirmacoes simultaneas
    CONSTRAINT appointment_no_overlapping_confirmed EXCLUDE USING gist (
        professional_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'confirmed')
);

CREATE INDEX appointment_professional_date_idx ON appointment (professional_id, date);
CREATE INDEX appointment_date_idx ON appointment (date);
CREATE INDEX appointment_pending_idx ON appointment (professional_id) WHERE status = 'pending';

CREATE TABLE appointment_status_history (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id UUID        NOT NULL REFERENCES appointment (id),
    from_status    TEXT        NOT NULL,
    to_status      TEXT        NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL,
    actor          TEXT        NOT NULL,
    reason         TEXT
);

CREATE INDEX appointment_status_history_appointment_idx ON appointment_status_history (appointment_id);

CREATE TABLE account (
    id               UUID PRIMARY KEY,
    -- Usuario normalizado (minusculas, sem espacos nas pontas)
    username         TEXT        NOT NULL UNIQUE,
    role             TEXT        NOT NULL,
    professional_id  TEXT UNIQUE REFERENCES professional (id),
    -- So o hash (BCrypt); a senha nunca e guardada
    password_hash    TEXT        NOT NULL,
    -- Incrementa a cada troca de senha e invalida as sessoes abertas da conta
    password_version INTEGER     NOT NULL DEFAULT 1,
    source           TEXT        NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT account_role_valid CHECK (role IN ('master', 'professional')),
    CONSTRAINT account_source_valid CHECK (source IN ('env', 'db')),
    CONSTRAINT account_professional_matches_role CHECK (
        (role = 'master' AND professional_id IS NULL) OR (role = 'professional' AND professional_id IS NOT NULL))
);

-- Outbox transacional: o evento e gravado na mesma transacao da mudanca e publicado depois pelo relay
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    -- Ordem de gravacao: o relay publica nessa ordem (eventos do mesmo agendamento saem em sequencia)
    seq          BIGSERIAL   NOT NULL,
    aggregate_id TEXT        NOT NULL,
    type         TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER     NOT NULL DEFAULT 0,
    last_error   TEXT
);

CREATE INDEX outbox_event_pending_idx ON outbox_event (seq) WHERE published_at IS NULL;
CREATE INDEX outbox_event_published_idx ON outbox_event (published_at) WHERE published_at IS NOT NULL;
