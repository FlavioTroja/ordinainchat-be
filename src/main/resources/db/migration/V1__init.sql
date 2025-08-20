-- Estensioni
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- per gen_random_uuid()

-- users (se non già creato nello step precedente)
CREATE TABLE IF NOT EXISTS users (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  version          BIGINT NOT NULL DEFAULT 0,
  telegram_user_id TEXT NOT NULL,
  current_step     TEXT NOT NULL DEFAULT 'START',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_users_telegram_user_id UNIQUE (telegram_user_id)
);
CREATE INDEX IF NOT EXISTS idx_users_telegram_user_id ON users(telegram_user_id);
