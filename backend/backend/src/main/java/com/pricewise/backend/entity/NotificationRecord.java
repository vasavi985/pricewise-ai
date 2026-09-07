package com.pricewise.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_records")
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_product_id")
    private TrackedProduct trackedProduct;

    private String recipientEmail;

    private Double previousPrice;

    private Double newPrice;

    private Double dropAmount;

    private Double dropPercentage;

    private String status = "PENDING"; // PENDING, SENT, FAILED, LOGGED

    @Column(length = 1000)
    private String message;

    private LocalDateTime sentAt;

    public NotificationRecord() {
    }

    public NotificationRecord(TrackedProduct trackedProduct, String recipientEmail, Double previousPrice, Double newPrice, Double dropAmount, Double dropPercentage, String status, String message) {
        this.trackedProduct = trackedProduct;
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

    public TrackedProduct getTrackedProduct() {
        return trackedProduct;
    }

    public void setTrackedProduct(TrackedProduct trackedProduct) {
        this.trackedProduct = trackedProduct;
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
