package it.overzoom.ordinainchat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, ProductSearchRepository {
}
