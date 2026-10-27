-- Savings goals (architecture §9.3). current_amount is maintained by the user; a contributions ledger is out of scope.
CREATE TABLE savings_goals (
    id             uuid          PRIMARY KEY,
    user_id        uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           text          NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    target_amount  numeric(19,4) NOT NULL CHECK (target_amount > 0),
    current_amount numeric(19,4) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
    currency       varchar(3)    NOT NULL,
    target_date    date          NOT NULL,
    status         text          NOT NULL CHECK (status IN ('ACTIVE', 'ACHIEVED', 'ARCHIVED')),
    priority       integer       NOT NULL DEFAULT 100,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL
);

CREATE INDEX savings_goals_user_idx ON savings_goals (user_id, priority, target_date);
