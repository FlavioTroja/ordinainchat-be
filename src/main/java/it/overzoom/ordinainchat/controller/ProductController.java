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

import it.overzoom.ordinainchat.dto.ProductDTO;
import it.overzoom.ordinainchat.exception.ResourceNotFoundException;
import it.overzoom.ordinainchat.mapper.ProductMapper;
import it.overzoom.ordinainchat.model.Product;
import it.overzoom.ordinainchat.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController extends BaseSearchController<Product, ProductDTO> {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(
            ProductService productService,
            ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @Override
    protected String getCollectionName() {
        return "product";
    }

    @Override
    protected Class<Product> getEntityClass() {
        return Product.class;
    }

    @Override
    protected Function<Product, ProductDTO> toDtoMapper() {
        return productMapper::toDto;
    }

    @Override
    protected List<String> getSearchableFields() {
        return List.of("name", "description", "category", "tags");
    }

    @GetMapping("")
    public ResponseEntity<Page<ProductDTO>> findAll(Pageable pageable) {
        log.info("REST request to get a page of Products");
        Page<Product> page = productService.findAll(pageable);
        return ResponseEntity.ok().body(page.map(productMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> findById(@PathVariable String id) throws ResourceNotFoundException {
        return productService.findById(id)
                .map(productMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto non trovato."));
    }

    @PostMapping("/create")
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO productDTO)
            throws BadRequestException, URISyntaxException {
        log.info("REST request to save Product : {}", productDTO);
        if (productDTO.getId() != null) {
            throw new BadRequestException("Un nuovo prodotto non può già avere un ID");
        }
        Product product = productMapper.toEntity(productDTO);
        product = productService.create(product);
        return ResponseEntity.created(new URI("/api/products/" + product.getId()))
                .body(productMapper.toDto(product));
    }

    @PutMapping("")
    public ResponseEntity<ProductDTO> update(@RequestBody ProductDTO productDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to update Product: {}", productDTO);
        if (productDTO.getId() == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!productService.existsById(productDTO.getId())) {
            throw new ResourceNotFoundException("Prodotto non trovato.");
        }
        Product product = productMapper.toEntity(productDTO);
        Product updated = productService.update(product)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prodotto non trovato con questo ID :: " + product.getId()));

        return ResponseEntity.ok().body(productMapper.toDto(updated));
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ProductDTO> partialUpdate(@PathVariable String id,
            @RequestBody ProductDTO productDTO)
            throws BadRequestException, ResourceNotFoundException {
        log.info("REST request to partial update Product: {}", productDTO);
        if (id == null) {
            throw new BadRequestException("ID invalido.");
        }
        if (!productService.existsById(id)) {
            throw new ResourceNotFoundException("Prodotto non trovato.");
        }
        Product product = productMapper.toEntity(productDTO);
        Product updated = productService.partialUpdate(id, product)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto non trovato con questo ID :: " + id));

        return ResponseEntity.ok().body(productMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable String id) throws ResourceNotFoundException {
        log.info("REST request to delete Product with ID: {}", id);
        if (!productService.existsById(id)) {
            throw new ResourceNotFoundException("Prodotto non trovato.");
        }
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
