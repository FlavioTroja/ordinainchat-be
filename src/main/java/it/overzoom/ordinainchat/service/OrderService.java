package it.overzoom.ordinainchat.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Order;

public interface OrderService {

    Page<Order> findAll(Pageable pageable);

    Optional<Order> findById(UUID id);

    boolean existsById(UUID id);

    Order create(Order order);

    Optional<Order> update(Order order);

    Optional<Order> partialUpdate(UUID id, Order partial);

    boolean deleteById(UUID id);
}
