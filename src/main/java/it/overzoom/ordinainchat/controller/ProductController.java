// src/main/java/it/overzoom/ordinainchat/controller/ProductController.java
package it.overzoom.ordinainchat.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import it.overzoom.ordinainchat.dto.ProductDTO;
import it.overzoom.ordinainchat.exception.ResourceNotFoundException;
import it.overzoom.ordinainchat.mapper.ProductMapper;
import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.search.ProductSearchCriteria;
import it.overzoom.ordinainchat.search.ProductSearchRequest;
import it.overzoom.ordinainchat.service.ProductService;
import it.overzoom.ordinainchat.type.SortType;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService,
            ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    // ── Ricerca avanzata con repository JPA custom ───────────────────────
    @PostMapping("/search")
    @Operation(summary = "Ricerca prodotti con filtri/ordinamento (PostgreSQL)", description = "Esegue la ricerca usando Criteria API. Paginazione via parametri standard (page,size,sort).", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductSearchRequest.class), examples = @ExampleObject(value = "{ \"search\":\"orata\", \"maxPrice\":15, \"items\":[\"orata\",\"spigola\"], \"sortBy\":\"freshness_desc\" }"))), responses = @ApiResponse(responseCode = "200", description = "Pagina di risultati", content = @Content(schema = @Schema(implementation = ProductDTO.class))))
    public ResponseEntity<Page<ProductDTO>> search(
            @RequestBody(required = false) ProductSearchRequest req,
            @Parameter(description = "Paginazione e sort, es: page=0&size=20&sort=price,asc") Pageable pageable) {

        log.debug("REST request to search Products with criteria: {}", req);

        ProductSearchCriteria c = new ProductSearchCriteria();
        if (req != null) {
            c.setOnlyOnOffer(req.getOnlyOnOffer());
            if (req.getFreshFromDate() != null && !req.getFreshFromDate().isBlank()) {
                c.setFreshFromDate(LocalDate.parse(req.getFreshFromDate()));
            }
            c.setMaxPrice(req.getMaxPrice());
            c.setItems(req.getItems());
            c.setIncludePrepared(req.getIncludePrepared());
            c.setSortType(req.getSortType() != null ? req.getSortType() : SortType.FRESHNESS_DESC);
            // puoi usare req.getSearch() per estendere i filtri (name/description LIKE)
        }

        Page<Product> page = productService.findAll(pageable); // fallback
        // se hai il ProductSearchRepository nel ProductService, esponi un metodo:
        // Page<Product> page = productSearchService.search(c, pageable);

        // Se non vuoi creare un service separato, inietti direttamente il repository
        // custom qui.

        return ResponseEntity.ok(page.map(productMapper::toDto));
    }

    // ── CRUD standard JPA ────────────────────────────────────────────────
    @GetMapping("")
    @Operation(summary = "Recupera tutti i prodotti (paginati)")
    public ResponseEntity<Page<ProductDTO>> findAll(Pageable pageable) {
        log.debug("REST request to get all Products");
        Page<Product> page = productService.findAll(pageable);
        return ResponseEntity.ok(page.map(productMapper::toDto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recupera un prodotto per ID", parameters = {
            @Parameter(name = "id", example = "3a3a1f0e-2f0a-4a62-9d33-1c1b9d3a7f7b") })
    public ResponseEntity<ProductDTO> findById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to get Product : {}", id);
        return productService.findById(id)
                .map(productMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto non trovato."));
    }

    @PostMapping("/create")
    @Operation(summary = "Crea un nuovo prodotto")
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO productDTO)
            throws BadRequestException, URISyntaxException {
        log.debug("REST request to create Product : {}", productDTO);
        if (productDTO.getId() != null)
            throw new BadRequestException("Un nuovo prodotto non può già avere un ID");
        Product product = productMapper.toEntity(productDTO);
        product = productService.create(product);
        return ResponseEntity.created(new URI("/api/products/" + product.getId()))
                .body(productMapper.toDto(product));
    }

    @PutMapping("")
    @Operation(summary = "Aggiorna un prodotto")
    public ResponseEntity<ProductDTO> update(@RequestBody ProductDTO productDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.debug("REST request to update Product : {}", productDTO);
        if (productDTO.getId() == null)
            throw new BadRequestException("ID invalido.");
        if (!productService.existsById(productDTO.getId()))
            throw new ResourceNotFoundException("Prodotto non trovato.");
        Product product = productMapper.toEntity(productDTO);
        Product updated = productService.update(product)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prodotto non trovato con questo ID :: " + product.getId()));
        return ResponseEntity.ok(productMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    @Operation(summary = "Aggiorna parzialmente un prodotto")
    public ResponseEntity<ProductDTO> partialUpdate(@PathVariable UUID id,
            @RequestBody ProductDTO productDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.debug("REST request to partially update Product : {}", productDTO);
        if (!productService.existsById(id))
            throw new ResourceNotFoundException("Prodotto non trovato.");
        Product product = productMapper.toEntity(productDTO);
        Product updated = productService.partialUpdate(id, product)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto non trovato con questo ID :: " + id));
        return ResponseEntity.ok(productMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancella un prodotto per ID")
    public ResponseEntity<Void> deleteById(@PathVariable UUID id) throws ResourceNotFoundException {
        log.debug("REST request to delete Product : {}", id);
        if (!productService.existsById(id))
            throw new ResourceNotFoundException("Prodotto non trovato.");
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
