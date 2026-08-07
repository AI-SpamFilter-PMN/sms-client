-- Neon (Postgres) schema for the whole AI spam filter project:
-- subscribers, SMS messages, voice calls, system logs, and a blocklist.
-- Written/maintained by whichever component owns each concern (the SMPP
-- server for messages, the SIP client for calls, etc).
--
-- Message bodies and call transcripts are intentionally NOT stored - only
-- the classification verdict and metadata are kept.

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid()

-- The users/numbers on the private network.
CREATE TABLE subscribers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    msisdn         VARCHAR(20) UNIQUE NOT NULL,
    imsi           VARCHAR(20) UNIQUE,
    display_name   VARCHAR(100),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLOCKED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SMS records, written after the SMPP server classifies and routes/blocks each one.
CREATE TABLE messages (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source                   VARCHAR(20) NOT NULL,
    source_subscriber_id     UUID REFERENCES subscribers (id) ON DELETE SET NULL,
    destination              VARCHAR(20) NOT NULL,
    destination_subscriber_id UUID REFERENCES subscribers (id) ON DELETE SET NULL,
    classification_label     VARCHAR(10) NOT NULL CHECK (classification_label IN ('spam', 'ham')),
    classification_score     DOUBLE PRECISION NOT NULL CHECK (classification_score BETWEEN 0 AND 1),
    status                   VARCHAR(20) NOT NULL CHECK (status IN ('DELIVERED', 'BLOCKED')),
    smpp_message_id          VARCHAR(65),
    received_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Voice call records, written after the SIP client's transcript is classified.
CREATE TABLE calls (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source                   VARCHAR(20) NOT NULL,
    source_subscriber_id     UUID REFERENCES subscribers (id) ON DELETE SET NULL,
    destination              VARCHAR(20) NOT NULL,
    destination_subscriber_id UUID REFERENCES subscribers (id) ON DELETE SET NULL,
    started_at               TIMESTAMPTZ NOT NULL,
    ended_at                 TIMESTAMPTZ,
    classification_label     VARCHAR(10) CHECK (classification_label IN ('spam', 'ham')),
    classification_score     DOUBLE PRECISION CHECK (classification_score BETWEEN 0 AND 1),
    status                   VARCHAR(20) NOT NULL
                                 CHECK (status IN ('COMPLETED', 'BLOCKED', 'MISSED', 'FAILED')),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- System/event log, decoupled from message and call content.
CREATE TABLE logs (
    id                 BIGSERIAL PRIMARY KEY,
    event_type         VARCHAR(40) NOT NULL,
    severity           VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARN', 'ERROR')),
    message            TEXT NOT NULL,
    related_message_id UUID REFERENCES messages (id) ON DELETE SET NULL,
    related_call_id    UUID REFERENCES calls (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Numbers explicitly blocked outright, checked before classification runs.
-- Not FK'd to subscribers - most blocked numbers were never registered.
CREATE TABLE blocklist (
    id         BIGSERIAL PRIMARY KEY,
    msisdn     VARCHAR(20) UNIQUE NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_messages_source ON messages (source);
CREATE INDEX idx_messages_destination ON messages (destination);
CREATE INDEX idx_messages_received_at ON messages (received_at DESC);

CREATE INDEX idx_calls_source ON calls (source);
CREATE INDEX idx_calls_destination ON calls (destination);
CREATE INDEX idx_calls_started_at ON calls (started_at DESC);

CREATE INDEX idx_logs_event_type ON logs (event_type);
CREATE INDEX idx_logs_created_at ON logs (created_at DESC);

-- Web app accounts (additive - owned entirely by the SMS client, does not
-- touch subscribers/messages/calls/logs/blocklist, which the SMPP server
-- already depends on). Login is by email since one user can own several
-- phone numbers - email is the single account identity, numbers are an
-- owned one-to-many resource.
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) UNIQUE NOT NULL,
    password     VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Phone numbers a user owns and can send SMS from. Deliberately matched to
-- messages.source/destination by raw string value, not a foreign key, so
-- this stays fully decoupled from the SMPP server's schema.
CREATE TABLE phone_numbers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    msisdn     VARCHAR(20) UNIQUE NOT NULL,
    label      VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_phone_numbers_user_id ON phone_numbers (user_id);
