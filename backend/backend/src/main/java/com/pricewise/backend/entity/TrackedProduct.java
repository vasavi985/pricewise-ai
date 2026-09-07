package com.pricewise.backend.entity;

import java.time.LocalDateTime;

public class TrackedProduct {

    private Long id;

    private Long storeProductId;

    private StoreProduct storeProduct;

    private String userId = "local-user";

    private String userEmail;

    private Double targetPrice;

    private Double targetDropPercentage;

    private Double initialPrice;

    private Double lastNotifiedPrice;

    private Boolean active = true;

    private LocalDateTime createdAt;

    private LocalDateTime lastCheckedAt;

    public TrackedProduct() {
        this.createdAt = LocalDateTime.now();
        this.lastCheckedAt = LocalDateTime.now();
        this.active = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStoreProductId() {
        return storeProductId != null ? storeProductId : (storeProduct != null ? storeProduct.getId() : null);
    }

    public void setStoreProductId(Long storeProductId) {
        this.storeProductId = storeProductId;
    }

    public StoreProduct getStoreProduct() {
        return storeProduct;
    }

    public void setStoreProduct(StoreProduct storeProduct) {
        this.storeProduct = storeProduct;
        if (storeProduct != null) {
            this.storeProductId = storeProduct.getId();
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Double getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(Double targetPrice) {
        this.targetPrice = targetPrice;
    }

    public Double getTargetDropPercentage() {
        return targetDropPercentage;
    }

    public void setTargetDropPercentage(Double targetDropPercentage) {
        this.targetDropPercentage = targetDropPercentage;
    }

    public Double getInitialPrice() {
        return initialPrice;
    }

    public void setInitialPrice(Double initialPrice) {
        this.initialPrice = initialPrice;
    }

    public Double getLastNotifiedPrice() {
        return lastNotifiedPrice;
    }

    public void setLastNotifiedPrice(Double lastNotifiedPrice) {
        this.lastNotifiedPrice = lastNotifiedPrice;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}
