package it.overzoom.ordinainchat.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTelegramUserId(String telegramUserId);

    boolean existsByTelegramUserId(String telegramUserId);
}
