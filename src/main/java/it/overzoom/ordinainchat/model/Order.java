package it.overzoom.ordinainchat.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ix_orders_order_date", columnList = "order_date"),
        @Index(name = "ix_orders_customer_id", columnList = "customer_id"),
        @Index(name = "ix_orders_user_id", columnList = "user_id")
})
public class Order extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", foreignKey = @ForeignKey(name = "fk_orders_customer"))
    private Customer customer;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_orders_user"))
    private User user;

    @ManyToMany
    @JoinTable(name = "order_products", joinColumns = @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_order_products_order")), inverseJoinColumns = @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_order_products_product")), uniqueConstraints = @UniqueConstraint(name = "uk_order_products_order_product", columnNames = {
            "order_id", "product_id" }))
    private List<Product> products = new ArrayList<>();

    // getters/setters
    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public OffsetDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(OffsetDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}
