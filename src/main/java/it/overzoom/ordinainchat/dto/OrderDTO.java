package it.overzoom.ordinainchat.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ordine con cliente, prodotti e data.")
public class OrderDTO extends BaseDTO {

    @Schema(description = "ID del cliente", example = "8d2e3f9c-7f2b-4bb7-8a6a-9a2e1f6b1b3c")
    private UUID customerId;

    @Schema(description = "Lista ID prodotti", example = "[\"5e0b1b10-2b7f-4e8f-9c5e-1f7b3a2d4c6e\"]")
    private List<UUID> productIds;

    @Schema(description = "Data/ora ordine (ISO 8601)", example = "2025-08-11T10:30:00+02:00")
    private OffsetDateTime orderDate;

    @Schema(description = "ID utente che gestisce l'ordine", example = "3f5f9a2b-1c7e-4830-9e8a-2d1c4b6a7e9f")
    private UUID userId;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public List<UUID> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<UUID> productIds) {
        this.productIds = productIds;
    }

    public OffsetDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(OffsetDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
