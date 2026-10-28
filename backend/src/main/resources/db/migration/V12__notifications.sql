-- In-app notifications (LLD §9, P5.5), created by NotificationHandler from domain events. source_event_id is unique, so
-- a re-delivered event can never create a second notification, whatever the event mode.
CREATE TABLE notifications (
    id              uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            text        NOT NULL CHECK (type IN ('BUDGET_THRESHOLD', 'ANOMALY', 'IMPORT_COMPLETED')),
    title           text        NOT NULL,
    body            text        NOT NULL,
    link            text        NOT NULL,
    source_event_id uuid        NOT NULL UNIQUE,
    read_at         timestamptz,
    created_at      timestamptz NOT NULL
);

CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);
