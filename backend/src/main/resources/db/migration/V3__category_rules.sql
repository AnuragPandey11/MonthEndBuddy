-- Deterministic auto-categorisation (architecture §7.1, §8.1). No user-supplied regex, so no ReDoS.
CREATE TABLE category_rules (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_type  text        NOT NULL CHECK (match_type IN ('CONTAINS', 'STARTS_WITH', 'EQUALS')),
    pattern     text        NOT NULL CHECK (char_length(pattern) BETWEEN 1 AND 100),
    category_id uuid        NOT NULL REFERENCES categories (id),
    priority    integer     NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 10000),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL
);

CREATE INDEX category_rules_user_priority_idx ON category_rules (user_id, priority, created_at);
