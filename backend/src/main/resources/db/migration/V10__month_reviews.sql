-- Month-end reviews (architecture §9.6): what the user did with what they kept in a finished month. One row per user
-- and month, written in the same transaction as the savings and goal updates, so a retried confirmation is rejected
-- instead of adding the money twice.
CREATE TABLE month_reviews (
    id                  uuid          PRIMARY KEY,
    user_id             uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    month_start         date          NOT NULL CHECK (extract(day FROM month_start) = 1),
    status              text          NOT NULL CHECK (status IN ('DONE', 'SKIPPED')),
    currency            varchar(3)    NOT NULL,
    kept_amount         numeric(19,4) NOT NULL,
    added_to_savings    numeric(19,4) CHECK (added_to_savings > 0),
    set_aside_for_goals numeric(19,4) CHECK (set_aside_for_goals > 0),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL
);

CREATE UNIQUE INDEX month_reviews_user_month_uq ON month_reviews (user_id, month_start);
