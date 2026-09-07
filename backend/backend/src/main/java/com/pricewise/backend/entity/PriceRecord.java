package com.pricewise.backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_records")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_product_id", nullable = false)
    @JsonBackReference
    private StoreProduct storeProduct;

    private String store; // AMAZON, FLIPKART, CROMA, OPEN_COMMERCE

    @Column(nullable = true)
    private Double price;

    private String currency = "INR";

    private String availability = "IN_STOCK"; // IN_STOCK, OUT_OF_STOCK, UNAVAILABLE

    private String status = "LIVE"; // LIVE, SAMPLE_DATA, FETCH_FAILED, UNAVAILABLE

    @Column(nullable = false)
    private LocalDateTime checkedAt;

    private String source = "LIVE"; // LIVE_API, CATALOG, SCHEDULER, MANUAL_CHECK

    public PriceRecord() {
    }

    public PriceRecord(StoreProduct storeProduct, Double price, String currency, String availability, String status, String source) {
        this.storeProduct = storeProduct;
        this.store = storeProduct != null ? storeProduct.getStore() : null;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability != null ? availability : "IN_STOCK";
        this.status = status != null ? status : "LIVE";
        this.source = source != null ? source : "LIVE";
        this.checkedAt = LocalDateTime.now();
    }

    // Legacy constructor compatibility
    public PriceRecord(StoreProduct storeProduct, Double price, String currency, String availability, String source) {
        this(storeProduct, price, currency, availability, storeProduct != null ? storeProduct.getStatus() : "LIVE", source);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StoreProduct getStoreProduct() {
        return storeProduct;
    }

    public void setStoreProduct(StoreProduct storeProduct) {
        this.storeProduct = storeProduct;
        if (storeProduct != null && this.store == null) {
            this.store = storeProduct.getStore();
        }
    }

    public String getStore() {
        return store != null ? store : (storeProduct != null ? storeProduct.getStore() : null);
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
