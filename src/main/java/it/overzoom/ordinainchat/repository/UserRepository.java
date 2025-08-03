package it.overzoom.ordinainchat.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.overzoom.ordinainchat.model.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByTelegramUserId(String telegramUserId);

}
