package com.pricewise.backend.repository;

import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.repository.firestore.FirestorePriceRecordRepository;
import com.pricewise.backend.repository.firestore.FirestoreSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestorePriceRecordRepositoryTest {

    private FirestorePriceRecordRepository repository;

    @BeforeEach
    void setUp() {
        FirestoreSequenceService sequenceService = new FirestoreSequenceService(null);
        repository = new FirestorePriceRecordRepository(null, sequenceService);
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
