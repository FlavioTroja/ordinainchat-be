package it.overzoom.ordinainchat.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.overzoom.ordinainchat.model.Product;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

}
