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
    @Operation(summary = "Recupera tutti i prodotti (paginati)", description = "Restituisce una pagina di prodotti, con possibilità di filtrare e ordinare tramite parametri standard di Spring (page, size, sort).", parameters = {
            @Parameter(name = "page", description = "Numero della pagina (0-based)", example = "0"),
            @Parameter(name = "size", description = "Numero di elementi per pagina", example = "20"),
            @Parameter(name = "sort", description = "Campo per ordinamento, es: 'name,asc'", example = "name,asc")
    }, responses = {
            @ApiResponse(responseCode = "200", description = "Pagina di prodotti", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDTO.class)))
    })
    public ResponseEntity<Page<ProductDTO>> findAll(
            @Parameter(description = "Parametri di paginazione e ordinamento") Pageable pageable) {
        log.info("REST request to get a page of Products");
        Page<Product> page = productService.findAll(pageable);
        return ResponseEntity.ok().body(page.map(productMapper::toDto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recupera un prodotto per ID", description = "Restituisce i dettagli di un prodotto specifico tramite il suo ID.", parameters = {
            @Parameter(name = "id", description = "ID del prodotto", required = true, example = "664f7f9fc2c9d664b2cf2d91")
    }, responses = {
            @ApiResponse(responseCode = "200", description = "Prodotto trovato", content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"Prodotto non trovato.\"}")))
    })
    public ResponseEntity<ProductDTO> findById(
            @PathVariable String id) throws ResourceNotFoundException {
        return productService.findById(id)
                .map(productMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto non trovato."));
    }

    @PostMapping("/create")
    @Operation(summary = "Crea un nuovo prodotto", description = "Crea un nuovo prodotto. Il campo ID non deve essere valorizzato nella richiesta.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Dati del prodotto da creare", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDTO.class), examples = @ExampleObject(value = "{ \"name\": \"Pizza Margherita\", \"description\": \"Pizza classica italiana\", \"category\": \"Pizza\", \"price\": 8.50 }"))), responses = {
            @ApiResponse(responseCode = "201", description = "Prodotto creato", content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "400", description = "ID fornito erroneamente per un nuovo prodotto", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"Un nuovo prodotto non può già avere un ID\"}")))
    })
    public ResponseEntity<ProductDTO> create(
            @RequestBody ProductDTO productDTO) throws BadRequestException, URISyntaxException {
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
    @Operation(summary = "Aggiorna un prodotto", description = "Aggiorna un prodotto esistente. L'ID deve essere presente nel payload.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Dati aggiornati del prodotto", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDTO.class), examples = @ExampleObject(value = "{ \"id\": \"664f7f9fc2c9d664b2cf2d91\", \"name\": \"Pizza Diavola\", \"description\": \"Pizza piccante\", \"category\": \"Pizza\", \"price\": 9.50 }"))), responses = {
            @ApiResponse(responseCode = "200", description = "Prodotto aggiornato", content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "400", description = "ID non presente o non valido", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"ID invalido.\"}"))),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"Prodotto non trovato.\"}")))
    })
    public ResponseEntity<ProductDTO> update(
            @RequestBody ProductDTO productDTO) throws BadRequestException, ResourceNotFoundException {
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
    @Operation(summary = "Aggiorna parzialmente un prodotto", description = "Aggiorna solo i campi specificati di un prodotto esistente.", parameters = {
            @Parameter(name = "id", description = "ID del prodotto da aggiornare", required = true, example = "664f7f9fc2c9d664b2cf2d91")
    }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Campi da aggiornare (solo quelli inclusi saranno modificati)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDTO.class), examples = @ExampleObject(value = "{ \"price\": 10.00 }"))), responses = {
            @ApiResponse(responseCode = "200", description = "Prodotto aggiornato", content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"Prodotto non trovato.\"}")))
    })
    public ResponseEntity<ProductDTO> partialUpdate(
            @PathVariable String id,
            @RequestBody ProductDTO productDTO) throws BadRequestException, ResourceNotFoundException {
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
    @Operation(summary = "Cancella un prodotto per ID", description = "Elimina un prodotto tramite il suo ID.", parameters = {
            @Parameter(name = "id", description = "ID del prodotto da eliminare", required = true, example = "664f7f9fc2c9d664b2cf2d91")
    }, responses = {
            @ApiResponse(responseCode = "204", description = "Prodotto eliminato con successo"),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"error\": \"Prodotto non trovato.\"}")))
    })
    public ResponseEntity<Void> deleteById(
            @PathVariable String id) throws ResourceNotFoundException {
        log.info("REST request to delete Product with ID: {}", id);
        if (!productService.existsById(id)) {
            throw new ResourceNotFoundException("Prodotto non trovato.");
        }
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
