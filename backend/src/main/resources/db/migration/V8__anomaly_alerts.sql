-- Explainable spending alerts (architecture §9.5). explanation holds the JSON numbers that triggered the rule.
CREATE TABLE anomaly_alerts (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rule_id      text        NOT NULL CHECK (rule_id IN ('LARGE_VS_CATEGORY', 'CATEGORY_SPIKE', 'ABSOLUTE_THRESHOLD')),
    subject_type text        NOT NULL CHECK (subject_type IN ('TRANSACTION', 'CATEGORY')),
    subject_id   uuid        NOT NULL,
    period_start date        NOT NULL,
    severity     text        NOT NULL CHECK (severity IN ('INFO', 'WARNING')),
    explanation  text        NOT NULL,
    status       text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DISMISSED', 'EXPECTED')),
    resolved_at  timestamptz,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL
);

-- One alert per rule, subject and month: rules can re-run safely, and a dismissed or expected alert stays resolved.
CREATE UNIQUE INDEX anomaly_alerts_dedupe_uq ON anomaly_alerts (user_id, rule_id, subject_type, subject_id, period_start);
CREATE INDEX anomaly_alerts_user_status_idx ON anomaly_alerts (user_id, status, period_start DESC);
