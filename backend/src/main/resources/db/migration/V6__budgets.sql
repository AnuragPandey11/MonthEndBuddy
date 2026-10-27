-- Monthly budgets, overall (category_id NULL) or per expense category (architecture §9.2).
CREATE TABLE budgets (
    id               uuid          PRIMARY KEY,
    user_id          uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id      uuid          REFERENCES categories (id),
    period           text          NOT NULL DEFAULT 'MONTHLY' CHECK (period IN ('MONTHLY')),
    limit_amount     numeric(19,4) NOT NULL CHECK (limit_amount > 0),
    currency         varchar(3)    NOT NULL,
    alert_thresholds integer[]     NOT NULL DEFAULT '{80,100}',
    active           boolean       NOT NULL DEFAULT true,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL
);

-- One active overall budget and one per category. NULLS NOT DISTINCT (PostgreSQL 15+) makes the overall budget unique.
CREATE UNIQUE INDEX budgets_active_uq ON budgets (user_id, category_id, period) NULLS NOT DISTINCT WHERE active;

-- Each threshold fires at most once per budget per month.
CREATE TABLE budget_threshold_events (
    budget_id    uuid        NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    period_start date        NOT NULL,
    threshold    integer     NOT NULL,
    fired_at     timestamptz NOT NULL,
    PRIMARY KEY (budget_id, period_start, threshold)
);
