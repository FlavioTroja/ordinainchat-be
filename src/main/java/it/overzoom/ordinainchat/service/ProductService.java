package it.overzoom.ordinainchat.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Product;

public interface ProductService {

    Page<Product> findAll(Pageable pageable);

    Optional<Product> findById(UUID id);

    boolean existsById(UUID id);

    Product create(Product product);

    Optional<Product> update(Product product);

    Optional<Product> partialUpdate(UUID id, Product product);

    boolean deleteById(UUID id);
}
