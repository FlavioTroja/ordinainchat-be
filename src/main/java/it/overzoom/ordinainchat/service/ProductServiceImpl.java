package it.overzoom.ordinainchat.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.repository.ProductRepository;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return productRepository.existsById(id);
    }

    @Override
    @Transactional
    public Product create(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Optional<Product> update(Product product) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(product.getId(), "product.id must not be null");

        return productRepository.findById(product.getId())
                .map(existing -> {
                    existing.setName(product.getName());
                    existing.setDescription(product.getDescription());
                    existing.setPrice(product.getPrice()); // BigDecimal
                    // se hai la relazione con User, gestiscila qui
                    return existing;
                })
                .map(productRepository::save);
    }

    @Override
    @Transactional
    public Optional<Product> partialUpdate(UUID id, Product patch) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        return productRepository.findById(id)
                .map(existing -> {
                    if (patch.getName() != null)
                        existing.setName(patch.getName());
                    if (patch.getDescription() != null)
                        existing.setDescription(patch.getDescription());
                    if (patch.getPrice() != null)
                        existing.setPrice(patch.getPrice());
                    return existing;
                })
                .map(productRepository::save);
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        try {
            productRepository.deleteById(id);
            return true;
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
    }
}