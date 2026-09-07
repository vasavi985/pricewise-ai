package com.pricewise.backend.dto;

public class ProviderProductDTO {
    private String store; // AMAZON, FLIPKART, CROMA, OPEN_COMMERCE, CATALOG
    private String storeProductId;
    private String title;
    private String canonicalName;
    private String brand;
    private String model;
    private String category;
    private String description;
    private String imageUrl;
    private String productUrl;
    private Double price;
    private String currency = "INR";
    private String availability = "IN_STOCK";
    private String status = "LIVE"; // LIVE, SAMPLE_DATA, CONFIG_REQUIRED, UNAVAILABLE, FETCH_FAILED
    private Double rating;

    public ProviderProductDTO() {
    }

    public ProviderProductDTO(String store, String storeProductId, String title, String canonicalName, String brand, String model, String category, String description, String imageUrl, String productUrl, Double price, String currency, String availability, String status, Double rating) {
        this.store = store;
        this.storeProductId = storeProductId;
        this.title = title;
        this.canonicalName = canonicalName;
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability != null ? availability : "IN_STOCK";
        this.status = status != null ? status : "LIVE";
        this.rating = rating;
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

    public String getCanonicalName() {
        return canonicalName != null ? canonicalName : title;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }
}
