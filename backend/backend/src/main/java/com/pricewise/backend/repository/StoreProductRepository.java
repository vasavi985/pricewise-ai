package com.pricewise.backend.repository;

import com.pricewise.backend.entity.StoreProduct;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository {
    List<StoreProduct> findAll();
    Optional<StoreProduct> findById(Long id);
    StoreProduct save(StoreProduct storeProduct);
    List<StoreProduct> findByProductId(Long productId);
    Optional<StoreProduct> findByProductIdAndStore(Long productId, String store);
    Optional<StoreProduct> findByStoreAndStoreProductId(String store, String storeProductId);
    long count();
    void deleteById(Long id);
}
