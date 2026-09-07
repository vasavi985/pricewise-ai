package com.pricewise.backend.dto;

import java.time.LocalDateTime;

public class TrackedProductDTO {
    private Long id;
    private Long storeProductId;
    private Long productId;
    private String productName;
    private String store;
    private String imageUrl;
    private String productUrl;
    private Double currentPrice;
    private Double initialPrice;
    private Double targetPrice;
    private Double targetDropPercentage;
    private Double lastNotifiedPrice;
    private Double priceDrop;
    private Double dropPercentage;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private boolean active;
    private String userEmail;

    public TrackedProductDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStoreProductId() {
        return storeProductId;
    }

    public void setStoreProductId(Long storeProductId) {
        this.storeProductId = storeProductId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Double getInitialPrice() {
        return initialPrice;
    }

    public void setInitialPrice(Double initialPrice) {
        this.initialPrice = initialPrice;
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

    public Double getLastNotifiedPrice() {
        return lastNotifiedPrice;
    }

    public void setLastNotifiedPrice(Double lastNotifiedPrice) {
        this.lastNotifiedPrice = lastNotifiedPrice;
    }

    public Double getPriceDrop() {
        return priceDrop;
    }

    public void setPriceDrop(Double priceDrop) {
        this.priceDrop = priceDrop;
    }

    public Double getDropPercentage() {
        return dropPercentage;
    }

    public void setDropPercentage(Double dropPercentage) {
        this.dropPercentage = dropPercentage;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
