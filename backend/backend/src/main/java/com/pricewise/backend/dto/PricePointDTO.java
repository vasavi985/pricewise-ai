package com.pricewise.backend.dto;

import java.time.LocalDateTime;

public class PricePointDTO {
    private String date; // formatted date (e.g., "2026-09-01")
    private LocalDateTime timestamp;
    private String store;
    private Double price;
    private String currency;
    private String availability;
    private String source;
    private String status;

    public PricePointDTO() {
    }

    public PricePointDTO(String date, LocalDateTime timestamp, String store, Double price, String currency, String availability, String source, String status) {
        this.date = date;
        this.timestamp = timestamp;
        this.store = store;
        this.price = price;
        this.currency = currency;
        this.availability = availability;
        this.source = source;
        this.status = status;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStore() {
        return store;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
