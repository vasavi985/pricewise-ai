package com.pricewise.backend.repository;

import com.pricewise.backend.entity.TrackedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedProductRepository extends JpaRepository<TrackedProduct, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"storeProduct", "storeProduct.product"})
    List<TrackedProduct> findByActiveTrue();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"storeProduct", "storeProduct.product"})
    List<TrackedProduct> findByUserIdAndActiveTrue(String userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"storeProduct", "storeProduct.product"})
    Optional<TrackedProduct> findById(Long id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"storeProduct", "storeProduct.product"})
    Optional<TrackedProduct> findByStoreProductIdAndUserIdAndActiveTrue(Long storeProductId, String userId);
}
