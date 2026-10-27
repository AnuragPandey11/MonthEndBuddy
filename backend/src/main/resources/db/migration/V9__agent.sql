-- Copilot conversations (architecture §10.9). tool_metadata stores the assistant turn's evidence snapshot as JSON,
-- so a reopened conversation re-renders its cards without calling any tool again.
CREATE TABLE agent_conversations (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);

CREATE INDEX agent_conversations_user_idx ON agent_conversations (user_id, updated_at DESC);

CREATE TABLE agent_messages (
    id                uuid        PRIMARY KEY,
    conversation_id   uuid        NOT NULL REFERENCES agent_conversations (id) ON DELETE CASCADE,
    user_id           uuid        NOT NULL,
    role              text        NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content           text        NOT NULL,
    tool_metadata     text,
    status            text,
    model             text,
    prompt_version    text,
    prompt_tokens     integer,
    completion_tokens integer,
    latency_ms        integer,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    version           bigint      NOT NULL
);

CREATE INDEX agent_messages_conversation_idx ON agent_messages (conversation_id, created_at);
