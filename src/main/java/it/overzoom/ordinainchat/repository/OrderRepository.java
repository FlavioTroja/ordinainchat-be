package it.overzoom.ordinainchat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.overzoom.ordinainchat.model.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
