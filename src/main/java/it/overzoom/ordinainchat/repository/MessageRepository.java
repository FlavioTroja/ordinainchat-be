package it.overzoom.ordinainchat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findTop50ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
