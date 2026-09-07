package com.pricewise.backend.repository;

import com.pricewise.backend.entity.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {
    List<StoreProduct> findByProductId(Long productId);
    Optional<StoreProduct> findByProductIdAndStore(Long productId, String store);
    Optional<StoreProduct> findByStoreAndStoreProductId(String store, String storeProductId);
}
