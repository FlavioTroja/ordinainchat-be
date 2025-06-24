package it.overzoom.ordinainchat.service;

import java.util.Optional;

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
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }

    @Override
    public boolean existsById(String id) {
        return productRepository.existsById(id);
    }

    @Override
    @Transactional
    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Optional<Product> update(Product product) {
        return productRepository.findById(product.getId()).map(existing -> {
            existing.setName(product.getName());
            existing.setDescription(product.getDescription());
            existing.setPrice(product.getPrice());
            return existing;
        }).map(productRepository::save);
    }

    @Override
    @Transactional
    public Optional<Product> partialUpdate(String id, Product product) {
        return productRepository.findById(id).map(existing -> {
            if (product.getName() != null)
                existing.setName(product.getName());
            if (product.getDescription() != null)
                existing.setDescription(product.getDescription());
            if (product.getPrice() != null)
                existing.setPrice(product.getPrice());
            return existing;
        }).map(productRepository::save);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        productRepository.deleteById(id);
    }

}
