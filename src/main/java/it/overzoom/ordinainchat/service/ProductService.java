package it.overzoom.ordinainchat.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Product;

public interface ProductService {

    Page<Product> findAll(Pageable pageable);

    Optional<Product> findById(String id);

    boolean existsById(String id);

    Product create(Product product);

    Optional<Product> update(Product product);

    Optional<Product> partialUpdate(String id, Product product);

    void deleteById(String id);
}
