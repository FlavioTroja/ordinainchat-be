package it.overzoom.ordinainchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO che rappresenta un prodotto. Contiene le informazioni principali di un prodotto gestito dall'applicazione.")
public class ProductDTO extends BaseDTO {

    @Schema(description = "Nome del prodotto", example = "Pizza Margherita", required = true)
    private String name;

    @Schema(description = "Descrizione del prodotto", example = "Pizza classica italiana con pomodoro, mozzarella e basilico")
    private String description;

    @Schema(description = "Prezzo del prodotto", example = "8.5", required = true)
    private double price;

    @Schema(description = "ID dell'utente proprietario del prodotto", example = "64efab20c4d65b2e82b7d09f")
    private String userId;

    // Getters e Setters

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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
