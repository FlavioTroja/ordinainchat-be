// src/main/java/it/overzoom/ordinainchat/controller/CustomerController.java
package it.overzoom.ordinainchat.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.overzoom.ordinainchat.dto.CustomerDTO;
import it.overzoom.ordinainchat.exception.ResourceNotFoundException;
import it.overzoom.ordinainchat.mapper.CustomerMapper;
import it.overzoom.ordinainchat.model.Customer;
import it.overzoom.ordinainchat.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService,
            CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @GetMapping("")
    public ResponseEntity<Page<CustomerDTO>> findAll(Pageable pageable) {
        log.debug("REST request to get all Customers");
        Page<Customer> page = customerService.findAll(pageable);
        return ResponseEntity.ok(page.map(customerMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDTO> findById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to get Customer : {}", id);
        return customerService.findById(id)
                .map(customerMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato."));
    }

    @PostMapping("/create")
    public ResponseEntity<CustomerDTO> create(@RequestBody CustomerDTO customerDTO)
            throws BadRequestException, URISyntaxException {
        log.debug("REST request to create Customer : {}", customerDTO);
        if (customerDTO.getId() != null)
            throw new BadRequestException("Un nuovo cliente non può già avere un ID");
        Customer customer = customerMapper.toEntity(customerDTO);
        customer = customerService.create(customer);
        return ResponseEntity.created(new URI("/api/customers/" + customer.getId()))
                .body(customerMapper.toDto(customer));
    }

    @PutMapping("")
    public ResponseEntity<CustomerDTO> update(@RequestBody CustomerDTO customerDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.debug("REST request to update Customer : {}", customerDTO);
        if (customerDTO.getId() == null)
            throw new BadRequestException("ID invalido.");
        if (!customerService.existsById(customerDTO.getId()))
            throw new ResourceNotFoundException("Cliente non trovato.");
        Customer updated = customerService.update(customerMapper.toEntity(customerDTO))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente non trovato con questo ID :: " + customerDTO.getId()));
        return ResponseEntity.ok(customerMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<CustomerDTO> partialUpdate(@PathVariable UUID id,
            @RequestBody CustomerDTO customerDTO)
            throws ResourceNotFoundException {
        log.debug("REST request to partially update Customer : {}", customerDTO);
        if (!customerService.existsById(id))
            throw new ResourceNotFoundException("Cliente non trovato.");
        Customer partial = customerMapper.toEntity(customerDTO);
        Customer updated = customerService.partialUpdate(id, partial)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato con questo ID :: " + id));
        return ResponseEntity.ok(customerMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to delete Customer : {}", id);
        if (!customerService.existsById(id))
            throw new ResourceNotFoundException("Cliente non trovato.");
        customerService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
