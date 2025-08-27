-- V4: add OpenAI conversation id to conversations
ALTER TABLE conversations
  ADD COLUMN IF NOT EXISTS openai_conversation_id varchar(128);

-- opzionale ma consigliato: unique index (consente più NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversations_openai_conversation_id
  ON conversations(openai_conversation_id);
