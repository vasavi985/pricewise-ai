package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.repository.firestore.FirestoreProductRepository;
import com.pricewise.backend.repository.firestore.FirestoreStoreProductRepository;
import com.pricewise.backend.repository.firestore.FirestoreTrackedProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreTrackedProductRepositoryTest {

    private FirestoreTrackedProductRepository trackedRepository;
    private FirestoreStoreProductRepository storeProductRepository;
    private FirestoreProductRepository productRepository;
    private DistributedIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new DistributedIdGenerator(4L);
        productRepository = new FirestoreProductRepository(null, idGenerator);
        storeProductRepository = new FirestoreStoreProductRepository(null, idGenerator);
        trackedRepository = new FirestoreTrackedProductRepository(null, idGenerator, storeProductRepository, productRepository);
    }

    @Test
    void testSave_NullIdGetsGeneratedLong() {
        TrackedProduct tp = new TrackedProduct();
        tp.setStoreProductId(50L);
        tp.setUserId("user-xyz");
        tp.setTargetPrice(2000.0);

        assertNull(tp.getId());
        TrackedProduct saved = trackedRepository.save(tp);

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertTrue(saved.getId() <= DistributedIdGenerator.MAX_SAFE_INTEGER);

        Optional<TrackedProduct> found = trackedRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("user-xyz", found.get().getUserId());
    }

    @Test
    void testPreserveExistingId_AndLegacyIdSupport() {
        TrackedProduct legacy = new TrackedProduct();
        legacy.setId(1L);
        legacy.setStoreProductId(1L);
        legacy.setUserId("legacy-user");
        legacy.setTargetPrice(50000.0);

        TrackedProduct saved = trackedRepository.save(legacy);
        assertEquals(1L, saved.getId(), "Existing manual/legacy ID 1L must be preserved");

        Optional<TrackedProduct> found = trackedRepository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getId());
        assertEquals("legacy-user", found.get().getUserId());
    }

    @Test
    void testTrackedProductHydrationAndQueries() {
        Product p = new Product();
        p.setCanonicalName("Sony Headphones");
        p = productRepository.save(p);

        StoreProduct sp = new StoreProduct();
        sp.setProductId(p.getId());
        sp.setStore("AMAZON");
        sp.setCurrentPrice(26000.0);
        sp = storeProductRepository.save(sp);

        TrackedProduct tp = new TrackedProduct();
        tp.setStoreProductId(sp.getId());
        tp.setUserId("user-1");
        tp.setUserEmail("user1@example.com");
        tp.setTargetPrice(25000.0);
        tp.setActive(true);
        trackedRepository.save(tp);

        List<TrackedProduct> active = trackedRepository.findByActiveTrue();
        assertEquals(1, active.size());
        assertNotNull(active.get(0).getStoreProduct());
        assertEquals("AMAZON", active.get(0).getStoreProduct().getStore());
        assertNotNull(active.get(0).getStoreProduct().getProduct());
        assertEquals("Sony Headphones", active.get(0).getStoreProduct().getProduct().getCanonicalName());

        Optional<TrackedProduct> foundByUser = trackedRepository.findByStoreProductIdAndUserIdAndActiveTrue(sp.getId(), "user-1");
        assertTrue(foundByUser.isPresent());
        assertEquals(25000.0, foundByUser.get().getTargetPrice());
    }
}
