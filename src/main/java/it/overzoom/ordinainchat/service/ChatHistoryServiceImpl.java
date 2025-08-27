package it.overzoom.ordinainchat.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.repository.ConversationRepository;
import it.overzoom.ordinainchat.repository.MessageRepository;

@Service
@Transactional
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ChatHistoryServiceImpl(ConversationRepository conversationRepository,
            MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public Conversation ensureConversation(UUID userId, String chatKey) {
        return conversationRepository
                .findFirstByUserIdAndTelegramChatId(userId, chatKey)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setUserId(userId);
                    c.setTelegramChatId(chatKey);
                    c.setTitle("Telegram chat " + chatKey);
                    // opzionale: set metadata/startedAt ecc. se non gestiti da @PrePersist
                    return conversationRepository.save(c);
                });
    }

    @Override
    public void save(Conversation conv) {
        conversationRepository.save(conv);
    }

    @Override
    @Transactional
    public void append(UUID conversationId, Message.Role role, String content, String model, Integer tokenCount) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setModel(model);
        m.setTokenCount(tokenCount);
        messageRepository.save(m);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> lastMessages(UUID conversationId, int limit) {
        List<Message> desc = messageRepository.findTop50ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(desc); // da più vecchi a più nuovi
        if (limit <= 0 || desc.size() <= limit)
            return desc;
        return desc.subList(desc.size() - limit, desc.size());
    }
}
