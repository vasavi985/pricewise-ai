package com.pricewise.backend.dto;

import java.time.LocalDateTime;

public class StorePriceDTO {
    private Long id;
    private String store;
    private String title;
    private Double price;
    private String currency;
    private String availability;
    private String productUrl;
    private String imageUrl;
    private String status; // LIVE, LAST_KNOWN, CONFIG_REQUIRED, UNAVAILABLE, SAMPLE_DATA
    private LocalDateTime lastCheckedAt;
    private boolean isLowest;

    public StorePriceDTO() {
    }

    public StorePriceDTO(Long id, String store, String title, Double price, String currency, String availability, String productUrl, String imageUrl, String status, LocalDateTime lastCheckedAt, boolean isLowest) {
        this.id = id;
        this.store = store;
        this.title = title;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability != null ? availability : "IN_STOCK";
        this.productUrl = productUrl;
        this.imageUrl = imageUrl;
        this.status = status != null ? status : "LIVE";
        this.lastCheckedAt = lastCheckedAt;
        this.isLowest = isLowest;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public boolean isLowest() {
        return isLowest;
    }

    public void setLowest(boolean lowest) {
        isLowest = lowest;
    }
}
