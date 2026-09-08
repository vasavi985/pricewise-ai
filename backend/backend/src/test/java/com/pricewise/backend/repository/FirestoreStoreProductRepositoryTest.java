package com.pricewise.backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.firestore.FirestoreStoreProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirestoreStoreProductRepositoryTest {

    private FirestoreStoreProductRepository repository;
    private DistributedIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DistributedIdGenerator(2L);
        repository = new FirestoreStoreProductRepository(null, idGenerator);
    }

    @Test
    void testSave_NullIdGetsGeneratedLong() {
        StoreProduct sp = new StoreProduct();
        sp.setProductId(100L);
        sp.setStore("AMAZON");
        sp.setCurrentPrice(49999.0);

        assertNull(sp.getId());
        StoreProduct saved = repository.save(sp);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertTrue(saved.getId() <= DistributedIdGenerator.MAX_SAFE_INTEGER);

        Optional<StoreProduct> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("AMAZON", found.get().getStore());
    }

    @Test
    void testPreserveExistingId_AndLegacyIdSupport() {
        StoreProduct legacy = new StoreProduct();
        legacy.setId(1L);
        legacy.setProductId(1L);
        legacy.setStore("CATALOG");
        legacy.setCurrentPrice(79900.0);

        StoreProduct saved = repository.save(legacy);
        assertEquals(1L, saved.getId(), "Existing manual/legacy ID 1L must be preserved");

        Optional<StoreProduct> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
        assertEquals("CATALOG", found.get().getStore());
    }

    @Test
    void testSaveAndFindByProductId() {
        StoreProduct sp1 = new StoreProduct();
        sp1.setProductId(100L);
        sp1.setStore("AMAZON");
        sp1.setStoreProductId("B09XYZ");
        sp1.setCurrentPrice(49999.0);
        repository.save(sp1);

        StoreProduct sp2 = new StoreProduct();
        sp2.setProductId(100L);
        sp2.setStore("FLIPKART");
        sp2.setStoreProductId("FK123");
        sp2.setCurrentPrice(48999.0);
        repository.save(sp2);

        List<StoreProduct> matches = repository.findByProductId(100L);
        assertEquals(2, matches.size());

        Optional<StoreProduct> amazon = repository.findByProductIdAndStore(100L, "AMAZON");
        assertTrue(amazon.isPresent());
        assertEquals(49999.0, amazon.get().getCurrentPrice());

        Optional<StoreProduct> flipkart = repository.findByStoreAndStoreProductId("FLIPKART", "FK123");
        assertTrue(flipkart.isPresent());
        assertEquals("FK123", flipkart.get().getStoreProductId());
    }

    @Test
    void testDeleteById() {
        StoreProduct sp = new StoreProduct();
        sp.setProductId(200L);
        sp.setStore("CATALOG");
        sp.setCurrentPrice(1000.0);
        StoreProduct saved = repository.save(sp);

        assertEquals(1, repository.count());

        repository.deleteById(saved.getId());
        assertEquals(0, repository.count());
        assertFalse(repository.findById(saved.getId()).isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExistingStoreProductsResolvedFromCache_WithoutRepeatedFirestoreQueries() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("storeProducts")).thenReturn(collection);
        when(collection.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc));

        when(doc.exists()).thenReturn(true);
        when(doc.getLong("id")).thenReturn(501L);
        when(doc.getId()).thenReturn("501");
        when(doc.getLong("productId")).thenReturn(101L);
        when(doc.getString("store")).thenReturn("AMAZON");
        when(doc.getString("storeProductId")).thenReturn("B001");
        when(doc.getString("title")).thenReturn("Wireless Headset");
        when(doc.getDouble("currentPrice")).thenReturn(3499.0);

        FirestoreStoreProductRepository repo = new FirestoreStoreProductRepository(firestore, idGenerator);
        assertFalse(repo.isCacheInitialized());

        // 1. First lookup triggers initialization from Firestore
        List<StoreProduct> byPid = repo.findByProductId(101L);
        assertEquals(1, byPid.size());
        assertEquals(501L, byPid.get(0).getId());
        assertTrue(repo.isCacheInitialized());

        // 2. Subsequent lookups (findByProductIdAndStore, findById, findAll, count) resolve directly from cache
        Optional<StoreProduct> amz = repo.findByProductIdAndStore(101L, "amazon");
        assertTrue(amz.isPresent());
        assertEquals(3499.0, amz.get().getCurrentPrice());

        Optional<StoreProduct> byId = repo.findById(501L);
        assertTrue(byId.isPresent());

        List<StoreProduct> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals(1, repo.count());

        // Verify collection.get() was called exactly ONCE across all operations!
        verify(collection, times(1)).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNewlySavedStoreProductsImmediatelyAvailableThroughCache() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> getFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("storeProducts")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document(anyString())).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestoreStoreProductRepository repo = new FirestoreStoreProductRepository(firestore, idGenerator);

        StoreProduct sp = new StoreProduct();
        sp.setProductId(202L);
        sp.setStore("OPEN_COMMERCE");
        sp.setStoreProductId("OC-99");
        sp.setTitle("Gaming Headset");
        sp.setCurrentPrice(2999.0);

        StoreProduct saved = repo.save(sp);
        assertNotNull(saved.getId());

        // Immediate cache resolution without extra Firestore queries
        Optional<StoreProduct> foundByStore = repo.findByProductIdAndStore(202L, "OPEN_COMMERCE");
        assertTrue(foundByStore.isPresent());
        assertEquals(saved.getId(), foundByStore.get().getId());
        assertEquals(2999.0, foundByStore.get().getCurrentPrice());

        List<StoreProduct> list = repo.findByProductId(202L);
        assertEquals(1, list.size());

        // Verify synchronous Firestore document write occurred
        verify(docRef, times(1)).set(anyMap(), any(SetOptions.class));
        verify(writeFuture, times(1)).get();
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

        when(firestore.collection("storeProducts")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document("1")).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestoreStoreProductRepository repo = new FirestoreStoreProductRepository(firestore, idGenerator);

        StoreProduct legacy = new StoreProduct();
        legacy.setId(1L);
        legacy.setProductId(1L);
        legacy.setStore("CATALOG");
        legacy.setCurrentPrice(79900.0);

        StoreProduct saved = repo.save(legacy);
        assertEquals(1L, saved.getId(), "Manual/legacy ID 1L must be preserved when Firestore is active");
        assertTrue(repo.findById(1L).isPresent());
        assertEquals(1L, repo.findById(1L).get().getId());
        verify(collection).document("1");
    }
}
