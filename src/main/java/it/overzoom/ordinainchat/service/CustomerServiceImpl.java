package it.overzoom.ordinainchat.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Customer;
import it.overzoom.ordinainchat.repository.CustomerRepository;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Customer> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return customerRepository.existsById(id);
    }

    @Override
    @Transactional
    public Customer create(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        // se usi @ManyToOne user, assicurati che sia managed o setta solo l'id
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Optional<Customer> update(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(customer.getId(), "customer.id must not be null");

        return customerRepository.findById(customer.getId())
                .map(existing -> {
                    existing.setName(customer.getName());
                    existing.setPhone(customer.getPhone());
                    existing.setAddress(customer.getAddress());
                    // se hai campi relazione (user) gestiscili qui
                    return existing;
                })
                .map(customerRepository::save);
    }

    @Override
    @Transactional
    public Optional<Customer> partialUpdate(UUID id, Customer customer) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customer, "customer must not be null");

        return customerRepository.findById(id)
                .map(existing -> {
                    if (customer.getName() != null)
                        existing.setName(customer.getName());
                    if (customer.getPhone() != null)
                        existing.setPhone(customer.getPhone());
                    if (customer.getAddress() != null)
                        existing.setAddress(customer.getAddress());
                    return existing;
                })
                .map(customerRepository::save);
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        try {
            customerRepository.deleteById(id);
            return true;
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
    }
}
