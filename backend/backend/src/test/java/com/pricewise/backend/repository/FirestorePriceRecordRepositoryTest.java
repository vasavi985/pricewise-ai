package com.pricewise.backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.repository.firestore.FirestorePriceRecordRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @Test
    @SuppressWarnings("unchecked")
    void testExistingPriceRecordsLoadedIntoLatestCache_WithoutRepeatedQueries() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);

        LocalDateTime t1 = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2025, 1, 2, 10, 0);

        when(firestore.collection("priceRecords")).thenReturn(collection);
        when(collection.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc1, doc2));

        // Doc 1: older price for storeProduct 50
        when(doc1.exists()).thenReturn(true);
        when(doc1.getLong("id")).thenReturn(1001L);
        when(doc1.getId()).thenReturn("1001");
        when(doc1.getLong("storeProductId")).thenReturn(50L);
        when(doc1.getLong("productId")).thenReturn(10L);
        when(doc1.getString("store")).thenReturn("AMAZON");
        when(doc1.getDouble("price")).thenReturn(1999.0);
        when(doc1.getString("checkedAt")).thenReturn(t1.toString());

        // Doc 2: newer price for storeProduct 50
        when(doc2.exists()).thenReturn(true);
        when(doc2.getLong("id")).thenReturn(1002L);
        when(doc2.getId()).thenReturn("1002");
        when(doc2.getLong("storeProductId")).thenReturn(50L);
        when(doc2.getLong("productId")).thenReturn(10L);
        when(doc2.getString("store")).thenReturn("AMAZON");
        when(doc2.getDouble("price")).thenReturn(1799.0);
        when(doc2.getString("checkedAt")).thenReturn(t2.toString());

        FirestorePriceRecordRepository repo = new FirestorePriceRecordRepository(firestore, idGenerator);
        assertFalse(repo.isCacheInitialized());

        // 1. Initial lookup populates cache from Firestore once
        Optional<PriceRecord> latest = repo.findFirstByStoreProductIdOrderByCheckedAtDesc(50L);
        assertTrue(latest.isPresent());
        assertEquals(1002L, latest.get().getId());
        assertEquals(1799.0, latest.get().getPrice());
        assertTrue(repo.isCacheInitialized());

        // 2. Repeated lookup uses cache without calling collection.get() again
        Optional<PriceRecord> latestAgain = repo.findFirstByStoreProductIdOrderByCheckedAtDesc(50L);
        assertTrue(latestAgain.isPresent());
        assertEquals(1799.0, latestAgain.get().getPrice());

        // 3. Historical records are preserved
        List<PriceRecord> all = repo.findAll();
        assertEquals(2, all.size());

        List<PriceRecord> history = repo.findByStoreProductIdOrderByCheckedAtAsc(50L);
        assertEquals(2, history.size());
        assertEquals(1999.0, history.get(0).getPrice());
        assertEquals(1799.0, history.get(1).getPrice());

        // Verify collection.get() called exactly ONCE!
        verify(collection, times(1)).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSavingNewerRecordUpdatesLatestCache_OlderRecordDoesNotOverwrite() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> getFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("priceRecords")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document(anyString())).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestorePriceRecordRepository repo = new FirestorePriceRecordRepository(firestore, idGenerator);

        LocalDateTime now = LocalDateTime.now();

        // 1. Save base price record
        PriceRecord base = new PriceRecord();
        base.setStoreProductId(80L);
        base.setPrice(5000.0);
        base.setCheckedAt(now);
        repo.save(base);

        assertEquals(5000.0, repo.findFirstByStoreProductIdOrderByCheckedAtDesc(80L).get().getPrice());

        // 2. Save NEWER price record -> MUST update latest cache
        PriceRecord newer = new PriceRecord();
        newer.setStoreProductId(80L);
        newer.setPrice(4500.0);
        newer.setCheckedAt(now.plusHours(1));
        repo.save(newer);

        assertEquals(4500.0, repo.findFirstByStoreProductIdOrderByCheckedAtDesc(80L).get().getPrice());

        // 3. Save OLDER price record (e.g. backfilled) -> MUST NOT overwrite newer in latest cache
        PriceRecord older = new PriceRecord();
        older.setStoreProductId(80L);
        older.setPrice(6000.0);
        older.setCheckedAt(now.minusHours(2));
        repo.save(older);

        // Latest price should STILL be 4500.0!
        assertEquals(4500.0, repo.findFirstByStoreProductIdOrderByCheckedAtDesc(80L).get().getPrice());

        // 4. All 3 historical records are preserved in in-memory repository
        List<PriceRecord> history = repo.findByStoreProductIdOrderByCheckedAtAsc(80L);
        assertEquals(3, history.size());
        assertEquals(6000.0, history.get(0).getPrice());
        assertEquals(5000.0, history.get(1).getPrice());
        assertEquals(4500.0, history.get(2).getPrice());

        // Verify synchronous Firestore document writes occurred
        verify(docRef, times(3)).set(anyMap(), any(SetOptions.class));
        verify(writeFuture, times(3)).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLegacyAndManualIdPreservedWithFirestoreConfigured() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> getFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("priceRecords")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document("1")).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestorePriceRecordRepository repo = new FirestorePriceRecordRepository(firestore, idGenerator);

        PriceRecord legacy = new PriceRecord();
        legacy.setId(1L);
        legacy.setProductId(1L);
        legacy.setStoreProductId(1L);
        legacy.setPrice(79900.0);

        PriceRecord saved = repo.save(legacy);
        assertEquals(1L, saved.getId(), "Manual/legacy ID 1L must be preserved when Firestore is active");
        assertTrue(repo.findById(1L).isPresent());
        assertEquals(1L, repo.findById(1L).get().getId());
        verify(collection).document("1");
    }
}
