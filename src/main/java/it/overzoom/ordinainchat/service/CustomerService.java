package it.overzoom.ordinainchat.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Customer;

public interface CustomerService {

    Page<Customer> findAll(Pageable pageable);

    Optional<Customer> findById(String id);

    boolean existsById(String id);

    Customer create(Customer customer);

    Optional<Customer> update(Customer customer);

    Optional<Customer> partialUpdate(String id, Customer customer);

    void deleteById(String id);
}
