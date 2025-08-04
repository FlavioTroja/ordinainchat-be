package it.overzoom.ordinainchat.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO che rappresenta un ordine, con riferimenti al cliente, ai prodotti acquistati, alla data ordine e all'utente.")
public class OrderDTO extends BaseDTO {

    @Schema(description = "ID del cliente che effettua l'ordine", example = "64f109c5f65ea94e37e0a6ba")
    private String customerId;

    @Schema(description = "Lista degli ID dei prodotti ordinati", example = "[\"64f10b26f65ea94e37e0a6bc\", \"64f10b26f65ea94e37e0a6bd\"]")
    private List<String> productIds;

    @Schema(description = "Data e ora in cui l'ordine è stato effettuato (formato ISO 8601)", example = "2024-08-04T15:30:00")
    private LocalDateTime orderDate;

    @Schema(description = "ID dell'utente che gestisce l'ordine", example = "64efab20c4d65b2e82b7d09f")
    private String userId;

    // Getters e Setters

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
