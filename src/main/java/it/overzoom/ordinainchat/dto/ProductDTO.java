package it.overzoom.ordinainchat.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Prodotto con nome, descrizione e prezzo.")
public class ProductDTO extends BaseDTO {

    @Schema(description = "Nome prodotto", example = "Orata fresca", required = true)
    private String name;

    @Schema(description = "Descrizione", example = "Pescato del giorno, provenienza Tirreno")
    private String description;

    @Schema(description = "Prezzo in EUR", example = "14.90", required = true)
    private BigDecimal price;

    @Schema(description = "ID utente proprietario", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID userId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
