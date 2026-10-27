-- Consumer idempotency (architecture §12.2): a handler records each event it processed in the same transaction as its
-- side effects, so a re-delivered event is skipped.
CREATE TABLE processed_events (
    consumer     text        NOT NULL,
    event_id     uuid        NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
