package it.overzoom.ordinainchat.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findFirstByUserIdAndTelegramChatId(UUID userId, String telegramChatId);
}
