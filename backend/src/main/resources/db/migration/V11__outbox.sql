-- Transactional outbox (architecture §12.2, P5.4). With meb.events.mode=kafka an event is written here in the same
-- transaction as the change it describes; OutboxRelay sends it to Kafka and stamps published_at. seq orders the relay,
-- message_key (the user id) keeps one user's events on one partition, and published rows are purged after 7 days.
CREATE TABLE outbox_events (
    id           uuid        PRIMARY KEY,
    seq          bigserial   NOT NULL,
    topic        text        NOT NULL,
    message_key  text        NOT NULL,
    event_type   text        NOT NULL,
    envelope     text        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX outbox_events_unpublished_idx ON outbox_events (seq) WHERE published_at IS NULL;
CREATE INDEX outbox_events_published_idx ON outbox_events (published_at) WHERE published_at IS NOT NULL;
