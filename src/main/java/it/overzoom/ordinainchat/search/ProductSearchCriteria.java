// src/main/java/it/overzoom/ordinainchat/search/ProductSearchCriteria.java
package it.overzoom.ordinainchat.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import it.overzoom.ordinainchat.type.FreshnessType;
import it.overzoom.ordinainchat.type.SortType;

public class ProductSearchCriteria {

    // filtri
    private Boolean onlyOnOffer; // true => solo in offerta
    private LocalDate freshFromDate; // es. LocalDate.now()
    private BigDecimal maxPrice; // EUR
    private List<String> items; // es. ["orata","spigola"]
    private BigDecimal minQuantityKg; // se gestisci giacenza
    private Boolean includePrepared; // includere marinati/pronti?
    private String search; // testo full-text su name/description (opzionale)
    private FreshnessType freshness; // es. FRESH, FROZEN
    private Boolean availableOnly;

    // ordinamento
    private SortType sortType; // es. FRESHNESS_DESC, PRICE_ASC

    // getters/setters
    public Boolean getOnlyOnOffer() {
        return onlyOnOffer;
    }

    public void setOnlyOnOffer(Boolean onlyOnOffer) {
        this.onlyOnOffer = onlyOnOffer;
    }

    public LocalDate getFreshFromDate() {
        return freshFromDate;
    }

    public void setFreshFromDate(LocalDate freshFromDate) {
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

    public BigDecimal getMinQuantityKg() {
        return minQuantityKg;
    }

    public void setMinQuantityKg(BigDecimal minQuantityKg) {
        this.minQuantityKg = minQuantityKg;
    }

    public Boolean getIncludePrepared() {
        return includePrepared;
    }

    public void setIncludePrepared(Boolean includePrepared) {
        this.includePrepared = includePrepared;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public SortType getSortType() {
        return sortType;
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
    }

    public FreshnessType getFreshness() {
        return freshness;
    }

    public void setFreshness(FreshnessType freshness) {
        this.freshness = freshness;
    }

    public Boolean getAvailableOnly() {
        return availableOnly;
    }

    public void setAvailableOnly(Boolean availableOnly) {
        this.availableOnly = availableOnly;
    }
}
