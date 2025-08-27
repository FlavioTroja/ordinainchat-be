ALTER TABLE conversations
  ADD CONSTRAINT uk_conversations_user_chat UNIQUE (user_id, telegram_chat_id);
