package it.overzoom.ordinainchat.service;

import java.util.Optional;

import it.overzoom.ordinainchat.model.User;

public interface UserService {

    User createWithTelegramId(String telegramUserId);

    Optional<User> findByTelegramUserId(String telegramUserId);
}
