// src/main/java/it/overzoom/ordinainchat/controller/OrderController.java
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

import it.overzoom.ordinainchat.dto.OrderDTO;
import it.overzoom.ordinainchat.exception.ResourceNotFoundException;
import it.overzoom.ordinainchat.mapper.OrderMapper;
import it.overzoom.ordinainchat.model.Order;
import it.overzoom.ordinainchat.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    public OrderController(OrderService orderService,
            OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    @GetMapping("")
    public ResponseEntity<Page<OrderDTO>> findAll(Pageable pageable) {
        log.debug("REST request to get all Orders");
        Page<Order> page = orderService.findAll(pageable);
        return ResponseEntity.ok(page.map(orderMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> findById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to get Order : {}", id);
        return orderService.findById(id)
                .map(orderMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine non trovato."));
    }

    @PostMapping("/create")
    public ResponseEntity<OrderDTO> create(@RequestBody OrderDTO orderDTO)
            throws BadRequestException, URISyntaxException {
        log.debug("REST request to create Order : {}", orderDTO);
        if (orderDTO.getId() != null)
            throw new BadRequestException("Un nuovo ordine non può già avere un ID");
        Order order = orderMapper.toEntity(orderDTO);
        order = orderService.create(order);
        return ResponseEntity.created(new URI("/api/orders/" + order.getId()))
                .body(orderMapper.toDto(order));
    }

    @PutMapping("")
    public ResponseEntity<OrderDTO> update(@RequestBody OrderDTO orderDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.debug("REST request to update Order : {}", orderDTO);
        if (orderDTO.getId() == null)
            throw new BadRequestException("ID invalido.");
        if (!orderService.existsById(orderDTO.getId()))
            throw new ResourceNotFoundException("Ordine non trovato.");
        Order updated = orderService.update(orderMapper.toEntity(orderDTO))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Ordine non trovato con questo ID :: " + orderDTO.getId()));
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<OrderDTO> partialUpdate(@PathVariable UUID id,
            @RequestBody OrderDTO orderDTO)
            throws ResourceNotFoundException {
        log.debug("REST request to partially update Order : {}", orderDTO);
        if (!orderService.existsById(id))
            throw new ResourceNotFoundException("Ordine non trovato.");
        Order partial = orderMapper.toEntity(orderDTO);
        Order updated = orderService.partialUpdate(id, partial)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine non trovato con questo ID :: " + id));
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to delete Order : {}", id);
        if (!orderService.existsById(id))
            throw new ResourceNotFoundException("Ordine non trovato.");
        orderService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
