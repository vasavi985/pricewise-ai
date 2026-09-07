package com.pricewise.backend.entity;

import java.time.LocalDateTime;

public class NotificationRecord {

    private Long id;

    private Long trackedProductId;

    private TrackedProduct trackedProduct;

    private String recipientEmail;

    private Double previousPrice;

    private Double newPrice;

    private Double dropAmount;

    private Double dropPercentage;

    private String status = "PENDING"; // PENDING, SENT, FAILED, LOGGED, EMAIL NOT CONFIGURED

    private String message;

    private LocalDateTime sentAt;

    public NotificationRecord() {
        this.sentAt = LocalDateTime.now();
    }

    public NotificationRecord(TrackedProduct trackedProduct, String recipientEmail, Double previousPrice, Double newPrice, Double dropAmount, Double dropPercentage, String status, String message) {
        this.trackedProduct = trackedProduct;
        this.trackedProductId = trackedProduct != null ? trackedProduct.getId() : null;
        this.recipientEmail = recipientEmail;
        this.previousPrice = previousPrice;
        this.newPrice = newPrice;
        this.dropAmount = dropAmount;
        this.dropPercentage = dropPercentage;
        this.status = status;
        this.message = message;
        this.sentAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTrackedProductId() {
        return trackedProductId != null ? trackedProductId : (trackedProduct != null ? trackedProduct.getId() : null);
    }

    public void setTrackedProductId(Long trackedProductId) {
        this.trackedProductId = trackedProductId;
    }

    public TrackedProduct getTrackedProduct() {
        return trackedProduct;
    }

    public void setTrackedProduct(TrackedProduct trackedProduct) {
        this.trackedProduct = trackedProduct;
        if (trackedProduct != null) {
            this.trackedProductId = trackedProduct.getId();
        }
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public Double getPreviousPrice() {
        return previousPrice;
    }

    public void setPreviousPrice(Double previousPrice) {
        this.previousPrice = previousPrice;
    }

    public Double getNewPrice() {
        return newPrice;
    }

    public void setNewPrice(Double newPrice) {
        this.newPrice = newPrice;
    }

    public Double getDropAmount() {
        return dropAmount;
    }

    public void setDropAmount(Double dropAmount) {
        this.dropAmount = dropAmount;
    }

    public Double getDropPercentage() {
        return dropPercentage;
    }

    public void setDropPercentage(Double dropPercentage) {
        this.dropPercentage = dropPercentage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
