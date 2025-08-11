package it.overzoom.ordinainchat.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Customer;

public interface CustomerService {
    Page<Customer> findAll(Pageable pageable);

    Optional<Customer> findById(UUID id);

    boolean existsById(UUID id);

    Customer create(Customer customer);

    Optional<Customer> update(Customer customer);

    Optional<Customer> partialUpdate(UUID id, Customer customer);

    boolean deleteById(UUID id);
}
