package it.overzoom.ordinainchat.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Customer;
import it.overzoom.ordinainchat.model.Order;
import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.repository.CustomerRepository;
import it.overzoom.ordinainchat.repository.OrderRepository;
import it.overzoom.ordinainchat.repository.ProductRepository;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderServiceImpl(OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    // ── READ ────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return orderRepository.existsById(id);
    }

    // ── CREATE ──────────────────────────────────────────────────────────
    @Override
    @Transactional
    public Order create(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        // Default orderDate
        if (order.getOrderDate() == null) {
            order.setOrderDate(OffsetDateTime.now());
        }

        // Associazioni: se sono valorizzate solo con id, ricaricale "managed"
        if (order.getCustomer() != null && order.getCustomer().getId() != null) {
            Customer cust = customerRepository.findById(order.getCustomer().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
            order.setCustomer(cust);
        } else if (order.getCustomer() == null) {
            throw new IllegalArgumentException("Order.customer is required");
        }

        if (order.getProducts() != null && !order.getProducts().isEmpty()) {
            order.setProducts(attachProductsByIds(order.getProducts()));
        } else {
            order.setProducts(new ArrayList<>());
        }

        return orderRepository.save(order);
    }

    // ── UPDATE (full) ───────────────────────────────────────────────────
    @Override
    @Transactional
    public Optional<Order> update(Order incoming) {
        Objects.requireNonNull(incoming, "order must not be null");
        Objects.requireNonNull(incoming.getId(), "order.id must not be null");

        return orderRepository.findById(incoming.getId()).map(existing -> {

            // Customer (full replace)
            if (incoming.getCustomer() != null && incoming.getCustomer().getId() != null) {
                Customer cust = customerRepository.findById(incoming.getCustomer().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
                existing.setCustomer(cust);
            }

            // Products (full replace)
            if (incoming.getProducts() != null) {
                existing.setProducts(attachProductsByIds(incoming.getProducts()));
            }

            // orderDate
            existing.setOrderDate(
                    incoming.getOrderDate() != null ? incoming.getOrderDate() : OffsetDateTime.now());

            // (se hai il campo user, gestiscilo qui come per customer)

            return existing;
        }).map(orderRepository::save);
    }

    // ── PATCH (parziale) ────────────────────────────────────────────────
    @Override
    @Transactional
    public Optional<Order> partialUpdate(UUID id, Order partial) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(partial, "partial must not be null");

        return orderRepository.findById(id).map(existing -> {

            if (partial.getCustomer() != null && partial.getCustomer().getId() != null) {
                Customer cust = customerRepository.findById(partial.getCustomer().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
                existing.setCustomer(cust);
            }

            if (partial.getProducts() != null) {
                existing.setProducts(attachProductsByIds(partial.getProducts()));
            }

            if (partial.getOrderDate() != null) {
                existing.setOrderDate(partial.getOrderDate());
            }

            return existing;
        }).map(orderRepository::save);
    }

    // ── DELETE ──────────────────────────────────────────────────────────
    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        try {
            orderRepository.deleteById(id);
            return true;
        } catch (EmptyResultDataAccessException ex) {
            return false; // id non esistente: idempotente
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────
    /**
     * Accetta una lista di Product con solo l'id valorizzato e li ricarica
     * "managed";
     * scarta null e duplicati, lancia se trova id inesistenti.
     */
    private List<Product> attachProductsByIds(List<Product> productsWithIdsOnly) {
        Set<UUID> ids = productsWithIdsOnly.stream()
                .filter(Objects::nonNull)
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (ids.isEmpty())
            return new ArrayList<>();

        List<Product> found = productRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            // trova quale manca
            Set<UUID> foundIds = found.stream().map(Product::getId).collect(Collectors.toSet());
            ids.removeAll(foundIds);
            throw new IllegalArgumentException("Some product IDs not found: " + ids);
        }
        return new ArrayList<>(found);
    }
}
