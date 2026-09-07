package com.pricewise.backend.dto;

import jakarta.validation.constraints.NotNull;

public class TrackingRequestDTO {

    @NotNull(message = "Store product ID is required")
    private Long storeProductId;

    private String userEmail;

    private Double targetPrice;

    private Double targetDropPercentage;

    private String userId = "local-user";

    public TrackingRequestDTO() {
    }

    public Long getStoreProductId() {
        return storeProductId;
    }

    public void setStoreProductId(Long storeProductId) {
        this.storeProductId = storeProductId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
