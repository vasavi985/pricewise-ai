package com.pricewise.backend.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.repository.firestore.FirestoreProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirestoreProductRepositoryTest {

    private FirestoreProductRepository repository;
    private DistributedIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DistributedIdGenerator(1L);
        repository = new FirestoreProductRepository(null, idGenerator);
    }

    @Test
    void testSaveAndFindById_NullIdGetsGeneratedLong() {
        Product p = new Product();
        p.setCanonicalName("Google Pixel 9");
        p.setBrand("Google");
        p.setCategory("Smartphones");

        assertNull(p.getId(), "Initial ID must be null");
        Product saved = repository.save(p);

        assertNotNull(saved.getId(), "Generated ID must not be null");
        assertTrue(saved.getId() > 0, "Generated ID must be a positive Long");
        assertTrue(saved.getId() <= DistributedIdGenerator.MAX_SAFE_INTEGER, "Generated ID must be within safe integer range");

        Optional<Product> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Google Pixel 9", found.get().getCanonicalName());
        assertEquals("Google", found.get().getBrand());
    }

    @Test
    void testPreserveExistingId_AndLegacyIdSupport() {
        // Test legacy ID 1L
        Product legacy1 = new Product();
        legacy1.setId(1L);
        legacy1.setCanonicalName("Apple iPhone 15");
        legacy1.setBrand("Apple");

        Product savedLegacy1 = repository.save(legacy1);
        assertEquals(1L, savedLegacy1.getId(), "Existing manual/legacy ID 1L must be strictly preserved");

        Optional<Product> foundLegacy1 = repository.findById(1L);
        assertTrue(foundLegacy1.isPresent());
        assertEquals("Apple iPhone 15", foundLegacy1.get().getCanonicalName());

        // Test legacy ID 2L
        Product legacy2 = new Product();
        legacy2.setId(2L);
        legacy2.setCanonicalName("Apple MacBook Air M2");
        legacy2.setBrand("Apple");

        Product savedLegacy2 = repository.save(legacy2);
        assertEquals(2L, savedLegacy2.getId(), "Existing manual/legacy ID 2L must be strictly preserved");

        Optional<Product> foundLegacy2 = repository.findById(2L);
        assertTrue(foundLegacy2.isPresent());
        assertEquals("Apple MacBook Air M2", foundLegacy2.get().getCanonicalName());
    }

    @Test
    void testSearchProducts_CaseInsensitive() {
        Product p1 = new Product();
        p1.setCanonicalName("MacBook Pro 16");
        p1.setBrand("Apple");
        repository.save(p1);

        Product p2 = new Product();
        p2.setCanonicalName("Dell XPS 15");
        p2.setBrand("Dell");
        repository.save(p2);

        List<Product> appleResults = repository.searchProducts("macbook");
        assertEquals(1, appleResults.size());
        assertEquals("MacBook Pro 16", appleResults.get(0).getCanonicalName());

        List<Product> dellResults = repository.searchProducts("xps");
        assertEquals(1, dellResults.size());
        assertEquals("Dell XPS 15", dellResults.get(0).getCanonicalName());

        List<Product> emptyQueryResults = repository.searchProducts("");
        assertEquals(2, emptyQueryResults.size());
    }

    @Test
    void testFindByCanonicalNameIgnoreCase() {
        Product p = new Product();
        p.setCanonicalName("iPad Air");
        p.setBrand("Apple");
        repository.save(p);

        Optional<Product> found = repository.findByCanonicalNameIgnoreCase("IPAD AIR");
        assertTrue(found.isPresent());
        assertEquals("iPad Air", found.get().getCanonicalName());

        Optional<Product> notFound = repository.findByCanonicalNameIgnoreCase("NonExistent");
        assertFalse(notFound.isPresent());
    }

    @Test
    void testDeleteById() {
        Product p = new Product();
        p.setCanonicalName("Temporary Gadget");
        Product saved = repository.save(p);

        assertEquals(1, repository.count());

        repository.deleteById(saved.getId());
        assertEquals(0, repository.count());
        assertFalse(repository.findById(saved.getId()).isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExistingFirestoreProductsResolvedFromCache_WithoutRepeatedFindAll() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("products")).thenReturn(collection);
        when(collection.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc));

        when(doc.exists()).thenReturn(true);
        when(doc.getLong("id")).thenReturn(42L);
        when(doc.getId()).thenReturn("42");
        when(doc.getString("productName")).thenReturn("Sony WH-1000XM5");
        when(doc.getString("canonicalName")).thenReturn("Sony WH-1000XM5");
        when(doc.getString("brand")).thenReturn("Sony");

        FirestoreProductRepository repo = new FirestoreProductRepository(firestore, idGenerator);
        assertFalse(repo.isCacheInitialized());

        // 1. First lookup triggers initialization from Firestore
        Optional<Product> p1 = repo.findByCanonicalNameIgnoreCase("sony wh-1000xm5");
        assertTrue(p1.isPresent());
        assertEquals(42L, p1.get().getId());
        assertEquals("Sony WH-1000XM5", p1.get().getCanonicalName());
        assertTrue(repo.isCacheInitialized());

        // 2. Repeated lookups resolve from cache and do NOT call firestore.collection("products").get() again
        Optional<Product> p2 = repo.findByCanonicalNameIgnoreCase("SONY WH-1000XM5");
        assertTrue(p2.isPresent());
        Optional<Product> p3 = repo.findByProductNameIgnoreCase("Sony WH-1000XM5");
        assertTrue(p3.isPresent());
        Optional<Product> p4 = repo.findById(42L);
        assertTrue(p4.isPresent());
        List<Product> search = repo.searchProducts("sony");
        assertEquals(1, search.size());
        List<Product> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals(1, repo.count());

        // Verify firestore.collection("products").get() was called EXACTLY once!
        verify(collection, times(1)).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNewlySavedProductsImmediatelyAvailableThroughCache() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> getFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("products")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document(anyString())).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestoreProductRepository repo = new FirestoreProductRepository(firestore, idGenerator);

        Product p = new Product();
        p.setCanonicalName("Bose QuietComfort 45");
        p.setBrand("Bose");
        Product saved = repo.save(p);

        assertNotNull(saved.getId());

        // Newly saved product is immediately resolvable through the cache
        Optional<Product> foundCanonical = repo.findByCanonicalNameIgnoreCase("bose quietcomfort 45");
        assertTrue(foundCanonical.isPresent());
        assertEquals(saved.getId(), foundCanonical.get().getId());

        Optional<Product> foundProduct = repo.findByProductNameIgnoreCase("bose quietcomfort 45");
        assertTrue(foundProduct.isPresent());

        Optional<Product> foundById = repo.findById(saved.getId());
        assertTrue(foundById.isPresent());
        assertEquals("Bose QuietComfort 45", foundById.get().getCanonicalName());

        // Verify synchronous Firestore write occurred
        verify(docRef, times(1)).set(anyMap(), any(SetOptions.class));
        verify(writeFuture, times(1)).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUpdatesRefreshCachedProduct() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        ApiFuture<QuerySnapshot> getFuture = mock(ApiFuture.class);
        QuerySnapshot emptySnapshot = mock(QuerySnapshot.class);

        when(firestore.collection("products")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document(anyString())).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestoreProductRepository repo = new FirestoreProductRepository(firestore, idGenerator);

        Product p = new Product();
        p.setCanonicalName("Sennheiser Momentum 4");
        p.setAmazonPrice(24990.0);
        Product saved = repo.save(p);

        assertEquals(24990.0, repo.findById(saved.getId()).get().getAmazonPrice());

        // Update product price
        saved.setAmazonPrice(21990.0);
        repo.save(saved);

        // Cached product immediately reflects the update
        assertEquals(21990.0, repo.findById(saved.getId()).get().getAmazonPrice());
        assertEquals(21990.0, repo.findByCanonicalNameIgnoreCase("sennheiser momentum 4").get().getAmazonPrice());
        assertEquals(21990.0, repo.findByProductNameIgnoreCase("sennheiser momentum 4").get().getAmazonPrice());
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

        when(firestore.collection("products")).thenReturn(collection);
        when(collection.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(emptySnapshot);
        when(emptySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        when(collection.document("1")).thenReturn(docRef);
        when(docRef.set(anyMap(), any(SetOptions.class))).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        FirestoreProductRepository repo = new FirestoreProductRepository(firestore, idGenerator);

        Product legacyProduct = new Product();
        legacyProduct.setId(1L);
        legacyProduct.setCanonicalName("Legacy Product #1");
        Product saved = repo.save(legacyProduct);

        assertEquals(1L, saved.getId(), "Manual/legacy ID must be strictly preserved when Firestore is active");
        assertTrue(repo.findById(1L).isPresent());
        assertEquals("Legacy Product #1", repo.findById(1L).get().getCanonicalName());
        verify(collection).document("1");
    }
}
