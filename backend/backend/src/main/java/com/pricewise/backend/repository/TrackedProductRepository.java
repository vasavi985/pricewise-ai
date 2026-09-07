package com.pricewise.backend.repository;

import com.pricewise.backend.entity.TrackedProduct;
import java.util.List;
import java.util.Optional;

public interface TrackedProductRepository {
    List<TrackedProduct> findAll();
    Optional<TrackedProduct> findById(Long id);
    TrackedProduct save(TrackedProduct trackedProduct);
    List<TrackedProduct> findByActiveTrue();
    List<TrackedProduct> findByUserIdAndActiveTrue(String userId);
    Optional<TrackedProduct> findByStoreProductIdAndUserIdAndActiveTrue(Long storeProductId, String userId);
    long count();
    void deleteById(Long id);
}
