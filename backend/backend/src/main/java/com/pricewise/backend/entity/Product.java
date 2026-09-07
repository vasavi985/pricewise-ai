package com.pricewise.backend.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Existing legacy fields for full backwards compatibility
    @Column(name = "product_name")
    private String productName;
    
    @Column(name = "amazon_price")
    private Double amazonPrice;
    
    @Column(name = "flipkart_price")
    private Double flipkartPrice;
    
    @Column(name = "croma_price")
    private Double cromaPrice;

    // Normalized intelligence fields
    private String canonicalName;
    private String brand;
    private String model;
    private String category;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String imageUrl;
    private Double rating;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<StoreProduct> storeProducts = new ArrayList<>();

    public Product() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.canonicalName == null && this.productName != null) {
            this.canonicalName = this.productName;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName != null ? productName : canonicalName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
        if (this.canonicalName == null) {
            this.canonicalName = productName;
        }
    }

    public Double getAmazonPrice() {
        return amazonPrice;
    }

    public void setAmazonPrice(Double amazonPrice) {
        this.amazonPrice = amazonPrice;
    }

    public Double getFlipkartPrice() {
        return flipkartPrice;
    }

    public void setFlipkartPrice(Double flipkartPrice) {
        this.flipkartPrice = flipkartPrice;
    }

    public Double getCromaPrice() {
        return cromaPrice;
    }

    public void setCromaPrice(Double cromaPrice) {
        this.cromaPrice = cromaPrice;
    }

    public String getCanonicalName() {
        return canonicalName != null ? canonicalName : productName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
        if (this.productName == null) {
            this.productName = canonicalName;
        }
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

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<StoreProduct> getStoreProducts() {
        return storeProducts;
    }

    public void setStoreProducts(List<StoreProduct> storeProducts) {
        this.storeProducts = storeProducts;
    }

    public void addStoreProduct(StoreProduct storeProduct) {
        storeProducts.add(storeProduct);
        storeProduct.setProduct(this);
    }
}