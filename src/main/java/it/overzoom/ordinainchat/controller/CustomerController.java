package it.overzoom.ordinainchat.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Function;

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
public class CustomerController extends BaseSearchController<Customer, CustomerDTO> {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(
            CustomerService customerService,
            CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @Override
    protected String getCollectionName() {
        return "customer";
    }

    @Override
    protected Class<Customer> getEntityClass() {
        return Customer.class;
    }

    @Override
    protected Function<Customer, CustomerDTO> toDtoMapper() {
        return customerMapper::toDto;
    }

    @Override
    protected List<String> getSearchableFields() {
        return List.of("name", "phone", "address");
    }

    @GetMapping("")
    public ResponseEntity<Page<CustomerDTO>> findAll(Pageable pageable) {
        log.info("REST request to get a page of Customers");
        Page<Customer> page = customerService.findAll(pageable);
        return ResponseEntity.ok().body(page.map(customerMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDTO> findById(@PathVariable String id) throws ResourceNotFoundException {
        return customerService.findById(id)
                .map(customerMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato."));
    }

    @PostMapping("/create")
    public ResponseEntity<CustomerDTO> create(@RequestBody CustomerDTO customerDTO)
            throws BadRequestException, URISyntaxException {
        log.info("REST request to save Customer : {}", customerDTO);
        if (customerDTO.getId() != null) {
            throw new BadRequestException("Un nuovo cliente non può già avere un ID");
        }
        Customer customer = customerMapper.toEntity(customerDTO);
        customer = customerService.create(customer);
        return ResponseEntity.created(new URI("/api/customers/" + customer.getId()))
                .body(customerMapper.toDto(customer));
    }

    @PutMapping("")
    public ResponseEntity<CustomerDTO> update(@RequestBody CustomerDTO customerDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to update Customer: {}", customerDTO);
        if (customerDTO.getId() == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!customerService.existsById(customerDTO.getId())) {
            throw new ResourceNotFoundException("Cliente non trovato.");
        }
        Customer customer = customerMapper.toEntity(customerDTO);
        Customer updated = customerService.update(customer)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente non trovato con questo ID :: " + customer.getId()));

        return ResponseEntity.ok().body(customerMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<CustomerDTO> partialUpdate(@PathVariable String id,
            @RequestBody CustomerDTO customerDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to partial update Customer: {}", customerDTO);
        if (id == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!customerService.existsById(id)) {
            throw new ResourceNotFoundException("Cliente non trovato.");
        }
        Customer customer = customerMapper.toEntity(customerDTO);
        Customer updated = customerService.partialUpdate(id, customer)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato con questo ID :: " + id));

        return ResponseEntity.ok().body(customerMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable String id) throws ResourceNotFoundException {
        log.info("REST request to delete Customer with ID: {}", id);
        if (!customerService.existsById(id)) {
            throw new ResourceNotFoundException("Cliente non trovato.");
        }
        customerService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
