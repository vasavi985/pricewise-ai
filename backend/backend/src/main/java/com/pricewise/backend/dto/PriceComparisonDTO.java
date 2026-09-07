package com.pricewise.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class PriceComparisonDTO {
    private Long productId;
    private String productName;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private Double rating;
    private Double lowestPrice;
    private Double highestPrice;
    private Double averagePrice;
    private Double savingsAmount;
    private Double savingsPercentage;
    private String bestStore;
    private List<StorePriceDTO> stores = new ArrayList<>();
    private String trendSummary;
    private String recommendation; // BUY NOW, GOOD PRICE, WAIT FOR LOWER PRICE, INSUFFICIENT DATA

    public PriceComparisonDTO() {
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

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Double getLowestPrice() {
        return lowestPrice;
    }

    public void setLowestPrice(Double lowestPrice) {
        this.lowestPrice = lowestPrice;
    }

    public Double getHighestPrice() {
        return highestPrice;
    }

    public void setHighestPrice(Double highestPrice) {
        this.highestPrice = highestPrice;
    }

    public Double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(Double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public Double getSavingsAmount() {
        return savingsAmount;
    }

    public void setSavingsAmount(Double savingsAmount) {
        this.savingsAmount = savingsAmount;
    }

    public Double getSavingsPercentage() {
        return savingsPercentage;
    }

    public void setSavingsPercentage(Double savingsPercentage) {
        this.savingsPercentage = savingsPercentage;
    }

    public String getBestStore() {
        return bestStore;
    }

    public void setBestStore(String bestStore) {
        this.bestStore = bestStore;
    }

    public List<StorePriceDTO> getStores() {
        return stores;
    }

    public void setStores(List<StorePriceDTO> stores) {
        this.stores = stores;
    }

    public String getTrendSummary() {
        return trendSummary;
    }

    public void setTrendSummary(String trendSummary) {
        this.trendSummary = trendSummary;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
