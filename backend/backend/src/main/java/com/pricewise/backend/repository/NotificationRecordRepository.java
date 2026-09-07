package com.pricewise.backend.repository;

import com.pricewise.backend.entity.NotificationRecord;
import java.util.List;
import java.util.Optional;

public interface NotificationRecordRepository {
    List<NotificationRecord> findAll();
    Optional<NotificationRecord> findById(Long id);
    NotificationRecord save(NotificationRecord record);
    List<NotificationRecord> findByTrackedProductIdOrderBySentAtDesc(Long trackedProductId);
    List<NotificationRecord> findTop20ByOrderBySentAtDesc();
    long count();
}
