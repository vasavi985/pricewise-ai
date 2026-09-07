package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(Long id);
    Product save(Product product);
    List<Product> searchProducts(String query);
    Optional<Product> findByCanonicalNameIgnoreCase(String canonicalName);
    Optional<Product> findByProductNameIgnoreCase(String productName);
    long count();
    void deleteById(Long id);
}