package it.overzoom.ordinainchat.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.overzoom.ordinainchat.model.Customer;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {

}
