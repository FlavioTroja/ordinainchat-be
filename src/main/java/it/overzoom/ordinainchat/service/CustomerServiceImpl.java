package it.overzoom.ordinainchat.service;

import java.util.Optional;

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
    public Page<Customer> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Override
    public Optional<Customer> findById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public boolean existsById(String id) {
        return customerRepository.existsById(id);
    }

    @Override
    @Transactional
    public Customer create(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Optional<Customer> update(Customer customer) {
        return customerRepository.findById(customer.getId()).map(existing -> {
            existing.setName(customer.getName());
            existing.setPhoneNumber(customer.getPhoneNumber());
            return existing;
        }).map(customerRepository::save);
    }

    @Override
    @Transactional
    public Optional<Customer> partialUpdate(String id, Customer customer) {
        return customerRepository.findById(id).map(existing -> {
            if (customer.getName() != null)
                existing.setName(customer.getName());
            if (customer.getPhoneNumber() != null)
                existing.setPhoneNumber(customer.getPhoneNumber());
            return existing;
        }).map(customerRepository::save);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        customerRepository.deleteById(id);
    }
}
