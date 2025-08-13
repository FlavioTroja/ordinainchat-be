package it.overzoom.ordinainchat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findTopByUserIdAndTelegramChatIdOrderByLastActivityAtDesc(UUID userId, String chatId);

    List<Conversation> findByUserIdAndTelegramChatIdOrderByLastActivityAtDesc(UUID userId, String chatId);
}
