package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE LOWER(p.productName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.canonicalName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Product> searchProducts(@Param("q") String query);

    Optional<Product> findByCanonicalNameIgnoreCase(String canonicalName);

    Optional<Product> findByProductNameIgnoreCase(String productName);
}