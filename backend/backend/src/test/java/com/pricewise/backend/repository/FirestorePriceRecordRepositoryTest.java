package com.pricewise.backend.repository;

import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.repository.firestore.FirestorePriceRecordRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestorePriceRecordRepositoryTest {

    private FirestorePriceRecordRepository repository;
    private DistributedIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DistributedIdGenerator(3L);
        repository = new FirestorePriceRecordRepository(null, idGenerator);
    }

    @Test
    void testSave_NullIdGetsGeneratedLong() {
        PriceRecord record = new PriceRecord();
        record.setProductId(10L);
        record.setStoreProductId(20L);
        record.setPrice(1500.0);

        assertNull(record.getId());
        PriceRecord saved = repository.save(record);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertTrue(saved.getId() <= DistributedIdGenerator.MAX_SAFE_INTEGER);

        Optional<PriceRecord> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(1500.0, found.get().getPrice());
    }

    @Test
    void testPreserveExistingId_AndLegacyIdSupport() {
        PriceRecord legacy = new PriceRecord();
        legacy.setId(1L);
        legacy.setProductId(1L);
        legacy.setStoreProductId(1L);
        legacy.setPrice(79900.0);

        PriceRecord saved = repository.save(legacy);
        assertEquals(1L, saved.getId(), "Existing manual/legacy ID 1L must be preserved");

        Optional<PriceRecord> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
    }

    @Test
    void testPriceRecordOrdering() {
        LocalDateTime now = LocalDateTime.now();

        PriceRecord r1 = new PriceRecord();
        r1.setProductId(1L);
        r1.setStoreProductId(10L);
        r1.setPrice(75000.0);
        r1.setCheckedAt(now.minusDays(2));
        repository.save(r1);

        PriceRecord r2 = new PriceRecord();
        r2.setProductId(1L);
        r2.setStoreProductId(10L);
        r2.setPrice(73000.0);
        r2.setCheckedAt(now.minusDays(1));
        repository.save(r2);

        PriceRecord r3 = new PriceRecord();
        r3.setProductId(1L);
        r3.setStoreProductId(10L);
        r3.setPrice(71000.0);
        r3.setCheckedAt(now);
        repository.save(r3);

        List<PriceRecord> ascList = repository.findByProductIdOrderByCheckedAtAsc(1L);
        assertEquals(3, ascList.size());
        assertEquals(75000.0, ascList.get(0).getPrice());
        assertEquals(71000.0, ascList.get(2).getPrice());

        Optional<PriceRecord> latest = repository.findFirstByStoreProductIdOrderByCheckedAtDesc(10L);
        assertTrue(latest.isPresent());
        assertEquals(71000.0, latest.get().getPrice());
    }
}
