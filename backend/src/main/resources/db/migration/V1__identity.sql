-- Identity module (architecture §5, §7). Emails are case-insensitive.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id            uuid        PRIMARY KEY,
    email         citext      NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    display_name  text        NOT NULL,
    is_demo       boolean     NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint      NOT NULL
);

CREATE TABLE user_settings (
    user_id                    uuid          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    base_currency              varchar(3)    NOT NULL DEFAULT 'INR',
    timezone                   text          NOT NULL DEFAULT 'Asia/Kolkata',
    current_savings            numeric(19,4) CHECK (current_savings >= 0),
    current_savings_updated_at timestamptz,
    emergency_buffer_months    integer       NOT NULL DEFAULT 3 CHECK (emergency_buffer_months BETWEEN 0 AND 24),
    large_txn_threshold        numeric(19,4) NOT NULL DEFAULT 25000 CHECK (large_txn_threshold > 0),
    anomaly_multiplier         numeric(4,2)  NOT NULL DEFAULT 3.0 CHECK (anomaly_multiplier BETWEEN 1.5 AND 10),
    category_spike_pct         integer       NOT NULL DEFAULT 40 CHECK (category_spike_pct BETWEEN 10 AND 500),
    created_at                 timestamptz   NOT NULL,
    updated_at                 timestamptz   NOT NULL,
    version                    bigint        NOT NULL
);

-- Opaque refresh tokens, stored as SHA-256 hashes, rotated within a family (architecture §5.2).
CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL UNIQUE,
    family_id   uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    created_at  timestamptz NOT NULL
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
