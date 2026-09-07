package com.pricewise.backend.repository;

import com.pricewise.backend.entity.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {
    List<NotificationRecord> findByTrackedProductIdOrderBySentAtDesc(Long trackedProductId);
    List<NotificationRecord> findTop20ByOrderBySentAtDesc();
}
