package com.pricewise.backend.repository;

import com.pricewise.backend.entity.NotificationRecord;
import com.pricewise.backend.repository.firestore.FirestoreNotificationRecordRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreNotificationRecordRepositoryTest {

    private FirestoreNotificationRecordRepository repository;
    private DistributedIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DistributedIdGenerator(5L);
        repository = new FirestoreNotificationRecordRepository(null, idGenerator);
    }

    @Test
    void testSave_NullIdGetsGeneratedLong() {
        NotificationRecord record = new NotificationRecord();
        record.setTrackedProductId(100L);
        record.setRecipientEmail("user@example.com");
        record.setMessage("Price dropped!");

        assertNull(record.getId());
        NotificationRecord saved = repository.save(record);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertTrue(saved.getId() <= DistributedIdGenerator.MAX_SAFE_INTEGER);

        Optional<NotificationRecord> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("user@example.com", found.get().getRecipientEmail());
    }

    @Test
    void testPreserveExistingId_AndLegacyIdSupport() {
        NotificationRecord legacy = new NotificationRecord();
        legacy.setId(1L);
        legacy.setTrackedProductId(10L);
        legacy.setRecipientEmail("legacy@example.com");

        NotificationRecord saved = repository.save(legacy);
        assertEquals(1L, saved.getId(), "Existing manual/legacy ID 1L must be preserved");

        Optional<NotificationRecord> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
    }

    @Test
    void testFindByTrackedProductId() {
        NotificationRecord r1 = new NotificationRecord();
        r1.setTrackedProductId(200L);
        r1.setMessage("Alert 1");
        repository.save(r1);

        NotificationRecord r2 = new NotificationRecord();
        r2.setTrackedProductId(200L);
        r2.setMessage("Alert 2");
        repository.save(r2);

        List<NotificationRecord> results = repository.findByTrackedProductIdOrderBySentAtDesc(200L);
        assertEquals(2, results.size());
    }
}
