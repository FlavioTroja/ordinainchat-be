package it.overzoom.ordinainchat.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.overzoom.ordinainchat.model.Order;
import it.overzoom.ordinainchat.repository.OrderRepository;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    public OrderServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Override
    public Optional<Order> findById(String id) {
        return orderRepository.findById(id);
    }

    @Override
    public boolean existsById(String id) {
        return orderRepository.existsById(id);
    }

    @Override
    @Transactional
    public Order create(Order order) {
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Optional<Order> update(Order order) {
        return orderRepository.findById(order.getId()).map(existing -> {
            existing.setCustomerId(order.getCustomerId());
            existing.setProductIds(order.getProductIds());
            existing.setOrderDate(order.getOrderDate() != null ? order.getOrderDate() : LocalDateTime.now());
            return existing;
        }).map(orderRepository::save);
    }

    @Override
    @Transactional
    public Optional<Order> partialUpdate(String id, Order order) {
        return orderRepository.findById(id).map(existing -> {
            if (order.getCustomerId() != null) {
                existing.setCustomerId(order.getCustomerId());
            }
            if (order.getProductIds() != null) {
                existing.setProductIds(order.getProductIds());
            }
            if (order.getOrderDate() != null) {
                existing.setOrderDate(order.getOrderDate());
            }
            return existing;
        }).map(orderRepository::save);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        orderRepository.deleteById(id);
    }
}
