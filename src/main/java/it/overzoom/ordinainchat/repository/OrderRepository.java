package it.overzoom.ordinainchat.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.overzoom.ordinainchat.model.Order;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

}
