package it.overzoom.ordinainchat.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import it.overzoom.ordinainchat.model.Order;

public interface OrderService {

    Page<Order> findAll(Pageable pageable);

    Optional<Order> findById(String id);

    boolean existsById(String id);

    Order create(Order order);

    Optional<Order> update(Order order);

    Optional<Order> partialUpdate(String id, Order order);

    void deleteById(String id);
}
