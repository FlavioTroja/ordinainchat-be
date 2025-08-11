// src/main/java/it/overzoom/ordinainchat/dto/ProductSearchRequest.java
package it.overzoom.ordinainchat.search;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import it.overzoom.ordinainchat.type.SortType;

@Schema(description = "Parametri di ricerca prodotti")
public class ProductSearchRequest {
    @Schema(description = "Testo full-text su name/description", example = "orata")
    private String search;

    @Schema(description = "Solo prodotti in offerta")
    private Boolean onlyOnOffer;

    @Schema(description = "Mostra solo arrivi da questa data (YYYY-MM-DD)", example = "2025-08-11")
    private String freshFromDate;

    @Schema(description = "Prezzo massimo", example = "15.00")
    private BigDecimal maxPrice;

    @Schema(description = "Specie richieste", example = "[\"orata\",\"spigola\"]")
    private List<String> items;

    @Schema(description = "Includere preparati/marinati", example = "false")
    private Boolean includePrepared;

    @Schema(description = "Ordinamento", example = "freshness_desc")
    private SortType sortType;

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Boolean getOnlyOnOffer() {
        return onlyOnOffer;
    }

    public void setOnlyOnOffer(Boolean onlyOnOffer) {
        this.onlyOnOffer = onlyOnOffer;
    }

    public String getFreshFromDate() {
        return freshFromDate;
    }

    public void setFreshFromDate(String freshFromDate) {
        this.freshFromDate = freshFromDate;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public Boolean getIncludePrepared() {
        return includePrepared;
    }

    public void setIncludePrepared(Boolean includePrepared) {
        this.includePrepared = includePrepared;
    }

    public SortType getSortType() {
        return sortType;
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
    }

}
