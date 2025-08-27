package it.overzoom.ordinainchat.service;

import java.util.List;
import java.util.UUID;

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.Message.Role;

public interface ChatHistoryService {

    Conversation ensureConversation(UUID userId, String chatId);

    void append(UUID conversationId, Role role, String content, String model, Integer tokens);

    List<Message> lastMessages(UUID conversationId, int max);

    void save(Conversation conv);
}
