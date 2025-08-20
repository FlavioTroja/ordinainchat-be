package it.overzoom.ordinainchat.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.User;
import it.overzoom.ordinainchat.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramUserId(String telegramUserId) {
        return userRepository.findByTelegramUserId(telegramUserId);
    }

    @Transactional
    public User createWithTelegramId(String telegramUserId) {
        User user = new User();
        user.setTelegramUserId(telegramUserId);
        // eventuale default per currentStep
        return userRepository.save(user);
    }

}
