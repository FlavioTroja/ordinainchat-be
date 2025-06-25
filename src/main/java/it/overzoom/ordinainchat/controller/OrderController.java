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

import it.overzoom.ordinainchat.dto.OrderDTO;
import it.overzoom.ordinainchat.exception.ResourceNotFoundException;
import it.overzoom.ordinainchat.mapper.OrderMapper;
import it.overzoom.ordinainchat.model.Order;
import it.overzoom.ordinainchat.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController extends BaseSearchController<Order, OrderDTO> {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    public OrderController(
            OrderService orderService,
            OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    @Override
    protected String getCollectionName() {
        return "order";
    }

    @Override
    protected Class<Order> getEntityClass() {
        return Order.class;
    }

    @Override
    protected Function<Order, OrderDTO> toDtoMapper() {
        return orderMapper::toDto;
    }

    @Override
    protected List<String> getSearchableFields() {
        return List.of("customerId", "productIds", "orderDate");
    }

    @GetMapping("")
    public ResponseEntity<Page<OrderDTO>> findAll(Pageable pageable) {
        log.info("REST request to get a page of Orders");
        Page<Order> page = orderService.findAll(pageable);
        return ResponseEntity.ok().body(page.map(orderMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> findById(@PathVariable String id) throws ResourceNotFoundException {
        return orderService.findById(id)
                .map(orderMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine non trovato."));
    }

    @PostMapping("/create")
    public ResponseEntity<OrderDTO> create(@RequestBody OrderDTO orderDTO)
            throws BadRequestException, URISyntaxException {
        log.info("REST request to save Order : {}", orderDTO);
        if (orderDTO.getId() != null) {
            throw new BadRequestException("Un nuovo ordine non può già avere un ID");
        }
        Order order = orderMapper.toEntity(orderDTO);
        order = orderService.create(order);
        return ResponseEntity.created(new URI("/api/orders/" + order.getId()))
                .body(orderMapper.toDto(order));
    }

    @PutMapping("")
    public ResponseEntity<OrderDTO> update(@RequestBody OrderDTO orderDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to update Order: {}", orderDTO);
        if (orderDTO.getId() == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!orderService.existsById(orderDTO.getId())) {
            throw new ResourceNotFoundException("Ordine non trovato.");
        }
        Order order = orderMapper.toEntity(orderDTO);
        Order updated = orderService.update(order)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ordine non trovato con questo ID :: " + order.getId()));

        return ResponseEntity.ok().body(orderMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<OrderDTO> partialUpdate(@PathVariable String id,
            @RequestBody OrderDTO orderDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to partial update Order: {}", orderDTO);
        if (id == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!orderService.existsById(id)) {
            throw new ResourceNotFoundException("Ordine non trovato.");
        }
        Order order = orderMapper.toEntity(orderDTO);
        Order updated = orderService.partialUpdate(id, order)
                .orElseThrow(() -> new ResourceNotFoundException("Ordine non trovato con questo ID :: " + id));

        return ResponseEntity.ok().body(orderMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable String id) throws ResourceNotFoundException {
        log.info("REST request to delete Order with ID: {}", id);
        if (!orderService.existsById(id)) {
            throw new ResourceNotFoundException("Ordine non trovato.");
        }
        orderService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
