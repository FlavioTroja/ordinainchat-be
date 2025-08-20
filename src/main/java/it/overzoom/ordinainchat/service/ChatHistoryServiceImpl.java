package it.overzoom.ordinainchat.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Conversation;
import it.overzoom.ordinainchat.model.Message;
import it.overzoom.ordinainchat.model.Message.Role;
import it.overzoom.ordinainchat.repository.ConversationRepository;
import it.overzoom.ordinainchat.repository.MessageRepository;

@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;

    public ChatHistoryServiceImpl(ConversationRepository conversationRepo, MessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    @Transactional
    public Conversation ensureConversation(UUID userId, String chatId) {
        return conversationRepo.findTopByUserIdAndTelegramChatIdOrderByLastActivityAtDesc(userId, chatId)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setUserId(userId);
                    c.setTelegramChatId(chatId);
                    c.setTitle("Telegram chat " + chatId);
                    return conversationRepo.save(c);
                });
    }

    @Transactional
    public Message append(UUID conversationId, Role role, String content, String model, Integer tokens) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setModel(model);
        m.setTokenCount(tokens);
        Message saved = messageRepo.save(m);

        // bump last activity
        conversationRepo.findById(conversationId).ifPresent(c -> {
            c.setLastActivityAt(java.time.OffsetDateTime.now());
            conversationRepo.save(c);
        });
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Message> lastMessages(UUID conversationId, int max) {
        List<Message> list = messageRepo.findTop50ByConversationIdOrderByCreatedAtDesc(conversationId);
        return list.size() <= max ? list : list.subList(0, max);
    }
}
