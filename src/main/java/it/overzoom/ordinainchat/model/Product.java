package it.overzoom.ordinainchat.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.overzoom.ordinainchat.type.FreshnessType;
import it.overzoom.ordinainchat.type.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "ix_products_price", columnList = "price_eur"),
        @Index(name = "ix_products_name", columnList = "name"),
        @Index(name = "ix_products_user_id", columnList = "user_id"),
        @Index(name = "ix_products_freshness", columnList = "freshness"),
        @Index(name = "ix_products_source", columnList = "source"),
        @Index(name = "ix_products_fao_area", columnList = "fao_area")
})
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_eur", precision = 10, scale = 2, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_products_user"))
    private User user;

    // ── Nuovi campi ────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "freshness", length = 20) // FRESH | FROZEN
    private FreshnessType freshness;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20) // WILD_CAUGHT | FARMED
    private SourceType source;

    // Provenienza
    @Column(name = "origin_country", length = 2) // ISO 3166-1 alpha-2 (es: IT, ES, GR)
    private String originCountry;

    @Column(name = "origin_area", length = 100) // es: "Mar Adriatico"
    private String originArea;

    @Column(name = "fao_area", length = 10) // es: "37.2.1"
    private String faoArea;

    @Column(name = "landing_port", length = 100) // es: "Chioggia"
    private String landingPort;

    // Date utili
    @Column(name = "catch_date") // data di pesca (se pescato)
    private LocalDate catchDate;

    @Column(name = "best_before") // TMC, utile per surgelato
    private LocalDate bestBefore;

    // Lavorazione/taglio (intero, eviscerato, filetto, trancI, etc.)
    @Column(name = "processing", length = 50)
    private String processing;

    // dentro Product
    @Column(name = "on_offer")
    private Boolean onOffer = Boolean.FALSE;

    @Column(name = "prepared")
    private Boolean prepared = Boolean.FALSE;

    @Column(name = "quantity_kg", precision = 10, scale = 3)
    private BigDecimal quantityKg;

    // getters/setters
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FreshnessType getFreshness() {
        return freshness;
    }

    public void setFreshness(FreshnessType freshness) {
        this.freshness = freshness;
    }

    public SourceType getSource() {
        return source;
    }

    public void setSource(SourceType source) {
        this.source = source;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    public String getOriginArea() {
        return originArea;
    }

    public void setOriginArea(String originArea) {
        this.originArea = originArea;
    }

    public String getFaoArea() {
        return faoArea;
    }

    public void setFaoArea(String faoArea) {
        this.faoArea = faoArea;
    }

    public String getLandingPort() {
        return landingPort;
    }

    public void setLandingPort(String landingPort) {
        this.landingPort = landingPort;
    }

    public LocalDate getCatchDate() {
        return catchDate;
    }

    public void setCatchDate(LocalDate catchDate) {
        this.catchDate = catchDate;
    }

    public LocalDate getBestBefore() {
        return bestBefore;
    }

    public void setBestBefore(LocalDate bestBefore) {
        this.bestBefore = bestBefore;
    }

    public String getProcessing() {
        return processing;
    }

    public void setProcessing(String processing) {
        this.processing = processing;
    }

    public Boolean getOnOffer() {
        return onOffer;
    }

    public void setOnOffer(Boolean onOffer) {
        this.onOffer = onOffer;
    }

    public Boolean getPrepared() {
        return prepared;
    }

    public void setPrepared(Boolean prepared) {
        this.prepared = prepared;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public void setQuantityKg(BigDecimal quantityKg) {
        this.quantityKg = quantityKg;
    }

}
