package com.pricewise.backend.repository;

import com.pricewise.backend.entity.PriceRecord;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository {
    List<PriceRecord> findAll();
    Optional<PriceRecord> findById(Long id);
    PriceRecord save(PriceRecord record);
    List<PriceRecord> findByStoreProductIdOrderByCheckedAtAsc(Long storeProductId);
    Optional<PriceRecord> findFirstByStoreProductIdOrderByCheckedAtDesc(Long storeProductId);
    List<PriceRecord> findByProductIdOrderByCheckedAtAsc(Long productId);
    List<PriceRecord> findByProductIdOrderByCheckedAtDesc(Long productId);
    long count();
}
