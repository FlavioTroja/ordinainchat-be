// src/main/java/it/overzoom/ordinainchat/repository/ProductSearchRepository.java
package it.overzoom.ordinainchat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.search.ProductSearchCriteria;

public interface ProductSearchRepository {
    Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);
}
