package com.pricewise.backend.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceHistoryDTO {
    private Long productId;
    private String productName;
    private Double currentLowest;
    private Double lowestRecorded;
    private Double highestRecorded;
    private Double averageRecorded;
    private String priceTrend; // FALLING, RISING, STABLE, INSUFFICIENT_DATA
    private java.time.LocalDateTime lastChecked;
    private int totalRecords;
    private List<PricePointDTO> points = new ArrayList<>();
    private Map<String, List<PricePointDTO>> storeSeries = new HashMap<>();

    public PriceHistoryDTO() {
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

    public Double getCurrentLowest() {
        return currentLowest;
    }

    public void setCurrentLowest(Double currentLowest) {
        this.currentLowest = currentLowest;
    }

    public Double getLowestRecorded() {
        return lowestRecorded;
    }

    public void setLowestRecorded(Double lowestRecorded) {
        this.lowestRecorded = lowestRecorded;
    }

    public Double getHighestRecorded() {
        return highestRecorded;
    }

    public void setHighestRecorded(Double highestRecorded) {
        this.highestRecorded = highestRecorded;
    }

    public Double getAverageRecorded() {
        return averageRecorded;
    }

    public void setAverageRecorded(Double averageRecorded) {
        this.averageRecorded = averageRecorded;
    }

    public String getPriceTrend() {
        return priceTrend;
    }

    public void setPriceTrend(String priceTrend) {
        this.priceTrend = priceTrend;
    }

    public java.time.LocalDateTime getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(java.time.LocalDateTime lastChecked) {
        this.lastChecked = lastChecked;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public List<PricePointDTO> getPoints() {
        return points;
    }

    public void setPoints(List<PricePointDTO> points) {
        this.points = points;
    }

    public Map<String, List<PricePointDTO>> getStoreSeries() {
        return storeSeries;
    }

    public void setStoreSeries(Map<String, List<PricePointDTO>> storeSeries) {
        this.storeSeries = storeSeries;
    }
}
