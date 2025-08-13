-- Conversazioni (un thread per utente/chat)
CREATE TABLE IF NOT EXISTS conversations (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  version           BIGINT NOT NULL DEFAULT 0,
  user_id           UUID REFERENCES users(id) ON DELETE SET NULL,
  telegram_chat_id  TEXT NOT NULL,                               -- chat specifica
  title             TEXT,
  metadata          JSONB,                                       -- es. impostazioni, tags...
  started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_activity_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_conversations_user_chat ON conversations(user_id, telegram_chat_id);
CREATE INDEX IF NOT EXISTS ix_conversations_last_activity ON conversations(last_activity_at DESC);
CREATE INDEX IF NOT EXISTS ix_conversations_metadata_gin ON conversations USING GIN (metadata);

-- Messaggi (turni utente/assistant/system)
CREATE TABLE IF NOT EXISTS messages (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  version          BIGINT NOT NULL DEFAULT 0,
  conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role             TEXT NOT NULL CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
  content          TEXT NOT NULL,
  model            TEXT,
  token_count      INT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_messages_conv_time ON messages(conversation_id, created_at);
