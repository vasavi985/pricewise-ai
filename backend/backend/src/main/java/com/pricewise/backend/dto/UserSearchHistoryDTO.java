package com.pricewise.backend.dto;

import java.time.LocalDateTime;

public class UserSearchHistoryDTO {

    private String id;
    private String userId;
    private String query;
    private Integer resultCount = 0;
    private String topProductName;
    private Double topProductPrice;
    private String topProductStore;
    private String topProductImage;
    private Long topProductId;
    private LocalDateTime createdAt;

    public UserSearchHistoryDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    public String getTopProductName() {
        return topProductName;
    }

    public void setTopProductName(String topProductName) {
        this.topProductName = topProductName;
    }

    public Double getTopProductPrice() {
        return topProductPrice;
    }

    public void setTopProductPrice(Double topProductPrice) {
        this.topProductPrice = topProductPrice;
    }

    public String getTopProductStore() {
        return topProductStore;
    }

    public void setTopProductStore(String topProductStore) {
        this.topProductStore = topProductStore;
    }

    public String getTopProductImage() {
        return topProductImage;
    }

    public void setTopProductImage(String topProductImage) {
        this.topProductImage = topProductImage;
    }

    public Long getTopProductId() {
        return topProductId;
    }

    public void setTopProductId(Long topProductId) {
        this.topProductId = topProductId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
