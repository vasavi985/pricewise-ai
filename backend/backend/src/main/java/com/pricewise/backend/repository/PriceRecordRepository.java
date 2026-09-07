package com.pricewise.backend.repository;

import com.pricewise.backend.entity.PriceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    List<PriceRecord> findByStoreProductIdOrderByCheckedAtAsc(Long storeProductId);

    Optional<PriceRecord> findFirstByStoreProductIdOrderByCheckedAtDesc(Long storeProductId);

    @Query("SELECT pr FROM PriceRecord pr JOIN FETCH pr.storeProduct sp WHERE sp.product.id = :productId ORDER BY pr.checkedAt ASC")
    List<PriceRecord> findByProductIdOrderByCheckedAtAsc(@Param("productId") Long productId);

    @Query("SELECT pr FROM PriceRecord pr JOIN FETCH pr.storeProduct sp WHERE sp.product.id = :productId ORDER BY pr.checkedAt DESC")
    List<PriceRecord> findByProductIdOrderByCheckedAtDesc(@Param("productId") Long productId);
}
