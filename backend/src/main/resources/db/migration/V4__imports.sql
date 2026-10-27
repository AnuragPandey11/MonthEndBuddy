-- Two-step CSV import (architecture D14, §8.4): rows are validated into a staging table, then committed.
CREATE TABLE import_jobs (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename           text        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('VALIDATED', 'COMMITTED', 'DISCARDED', 'FAILED')),
    date_format        text        NOT NULL CHECK (date_format IN ('ISO_YYYY_MM_DD', 'DD_MM_YYYY')),
    total_rows         integer     NOT NULL,
    valid_rows         integer     NOT NULL,
    invalid_rows       integer     NOT NULL,
    duplicate_rows     integer     NOT NULL,
    imported_rows      integer,
    skipped_duplicates integer,
    skipped_invalid    integer,
    failure_reason     text,
    committed_at       timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL
);

CREATE INDEX import_jobs_user_idx ON import_jobs (user_id, created_at DESC);

-- Staged rows. Purged 7 days after the job finishes, so raw statement data is not kept.
-- errors/warnings hold JSON arrays of {field, message}; they are never queried, only shown.
CREATE TABLE import_rows (
    id               uuid          PRIMARY KEY,
    import_job_id    uuid          NOT NULL REFERENCES import_jobs (id) ON DELETE CASCADE,
    user_id          uuid          NOT NULL,
    row_number       integer       NOT NULL,
    status           text          NOT NULL CHECK (status IN ('VALID', 'INVALID', 'DUPLICATE')),
    transaction_date date,
    type             text,
    amount           numeric(19,4),
    currency         varchar(3),
    description      text,
    merchant         text,
    category_name    text,
    category_id      uuid,
    notes            text,
    fingerprint      text,
    fingerprint_seq  integer,
    errors           text          NOT NULL DEFAULT '[]',
    warnings         text          NOT NULL DEFAULT '[]',
    created_at       timestamptz   NOT NULL
);

CREATE INDEX import_rows_job_idx ON import_rows (import_job_id, status, row_number);

ALTER TABLE transactions
    ADD CONSTRAINT transactions_import_job_fk FOREIGN KEY (import_job_id) REFERENCES import_jobs (id) ON DELETE SET NULL;
