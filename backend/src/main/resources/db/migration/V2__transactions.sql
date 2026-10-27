-- Transactions module: categories and transactions (architecture §7, §8).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- user_id NULL = system category, visible to everyone and read-only.
CREATE TABLE categories (
    id         uuid        PRIMARY KEY,
    user_id    uuid        REFERENCES users (id) ON DELETE CASCADE,
    name       text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    kind       text        NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME')),
    icon       text,
    archived   boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);

-- One name per owner (system or user), case-insensitively. Clashes with system names are checked in CategoryService.
CREATE UNIQUE INDEX categories_owner_name_uq
    ON categories (coalesce(user_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));
CREATE INDEX categories_user_idx ON categories (user_id);

INSERT INTO categories (id, user_id, name, kind, icon, created_at, updated_at, version) VALUES
    ('0192f000-0000-7000-8000-000000000001', NULL, 'Rent',              'EXPENSE', 'home',           now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000002', NULL, 'Groceries',         'EXPENSE', 'shopping-cart',  now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000003', NULL, 'Dining',            'EXPENSE', 'utensils',       now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000004', NULL, 'Transport',         'EXPENSE', 'bus',            now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000005', NULL, 'Utilities',         'EXPENSE', 'zap',            now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000006', NULL, 'Shopping',          'EXPENSE', 'shopping-bag',   now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000007', NULL, 'Entertainment',     'EXPENSE', 'film',           now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000008', NULL, 'Health',            'EXPENSE', 'heart-pulse',    now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000009', NULL, 'Education',         'EXPENSE', 'graduation-cap', now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000a', NULL, 'Travel',            'EXPENSE', 'plane',          now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000b', NULL, 'Subscriptions',     'EXPENSE', 'repeat',         now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000c', NULL, 'Personal Care',     'EXPENSE', 'sparkles',       now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000d', NULL, 'Insurance',         'EXPENSE', 'shield',         now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000e', NULL, 'EMI & Loans',       'EXPENSE', 'landmark',       now(), now(), 0),
    ('0192f000-0000-7000-8000-00000000000f', NULL, 'Gifts & Donations', 'EXPENSE', 'gift',           now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000010', NULL, 'Other Expense',     'EXPENSE', 'circle-dashed',  now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000101', NULL, 'Salary',            'INCOME',  'briefcase',      now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000102', NULL, 'Freelance',         'INCOME',  'laptop',         now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000103', NULL, 'Interest',          'INCOME',  'percent',        now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000104', NULL, 'Refund',            'INCOME',  'undo-2',         now(), now(), 0),
    ('0192f000-0000-7000-8000-000000000105', NULL, 'Other Income',      'INCOME',  'plus-circle',    now(), now(), 0);

-- The amount is always positive; type carries the sign (architecture §6.1).
CREATE TABLE transactions (
    id               uuid          PRIMARY KEY,
    user_id          uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type             text          NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount           numeric(19,4) NOT NULL CHECK (amount > 0),
    currency         varchar(3)    NOT NULL,
    transaction_date date          NOT NULL,
    description      text          NOT NULL CHECK (char_length(description) BETWEEN 1 AND 255),
    merchant         text          CHECK (char_length(merchant) <= 120),
    category_id      uuid          REFERENCES categories (id),
    category_source  text          NOT NULL DEFAULT 'NONE'
                                   CHECK (category_source IN ('USER', 'RULE', 'AI', 'IMPORT', 'NONE')),
    notes            text          CHECK (char_length(notes) <= 1000),
    source           text          NOT NULL CHECK (source IN ('MANUAL', 'CSV_IMPORT', 'DEMO_SEED')),
    import_job_id    uuid,         -- FK to import_jobs is added with that table (V4)
    fingerprint      text,
    fingerprint_seq  integer,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL
);

-- Keyset pagination and period aggregates.
CREATE INDEX transactions_user_date_idx ON transactions (user_id, transaction_date DESC, id DESC);
-- Category breakdown, anomaly baselines, budget spend.
CREATE INDEX transactions_user_category_date_idx ON transactions (user_id, category_id, transaction_date);
-- Duplicate-import prevention (architecture §8.3). Partial: ON CONFLICT must repeat the WHERE clause.
CREATE UNIQUE INDEX transactions_fingerprint_uq ON transactions (user_id, fingerprint, fingerprint_seq)
    WHERE fingerprint IS NOT NULL;
-- Text filter. TransactionQueryRepository uses exactly this expression so the index applies.
CREATE INDEX transactions_search_trgm_idx ON transactions
    USING gin (lower(description || ' ' || coalesce(merchant, '')) gin_trgm_ops);
