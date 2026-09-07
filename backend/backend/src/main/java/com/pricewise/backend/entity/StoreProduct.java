package com.pricewise.backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "store_products")
public class StoreProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonBackReference
    private Product product;

    @Column(nullable = false)
    private String store; // AMAZON, FLIPKART, CROMA, OPEN_COMMERCE, CATALOG

    private String storeProductId;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String productUrl;

    @Column(length = 1000)
    private String imageUrl;

    private Double currentPrice;

    private String currency = "INR";

    private String availability = "IN_STOCK"; // IN_STOCK, OUT_OF_STOCK, UNAVAILABLE

    private String status = "LIVE"; // LIVE, LAST_KNOWN, CONFIG_REQUIRED, FETCH_FAILED, SAMPLE_DATA

    private LocalDateTime lastCheckedAt;

    @OneToMany(mappedBy = "storeProduct", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<PriceRecord> priceRecords = new ArrayList<>();

    public StoreProduct() {
    }

    public StoreProduct(Product product, String store, String storeProductId, String title, String productUrl, Double currentPrice, String currency, String availability, String status) {
        this.product = product;
        this.store = store;
        this.storeProductId = storeProductId;
        this.title = title;
        this.productUrl = productUrl;
        this.currentPrice = currentPrice;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability != null ? availability : "IN_STOCK";
        this.status = status != null ? status : "LIVE";
        this.lastCheckedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getStoreProductId() {
        return storeProductId;
    }

    public void setStoreProductId(String storeProductId) {
        this.storeProductId = storeProductId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
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

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public List<PriceRecord> getPriceRecords() {
        return priceRecords;
    }

    public void setPriceRecords(List<PriceRecord> priceRecords) {
        this.priceRecords = priceRecords;
    }

    public void addPriceRecord(PriceRecord record) {
        priceRecords.add(record);
        record.setStoreProduct(this);
    }
}
