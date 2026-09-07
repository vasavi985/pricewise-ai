package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.repository.firestore.FirestoreProductRepository;
import com.pricewise.backend.repository.firestore.FirestoreSequenceService;
import com.pricewise.backend.repository.firestore.FirestoreStoreProductRepository;
import com.pricewise.backend.repository.firestore.FirestoreTrackedProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreTrackedProductRepositoryTest {

    private FirestoreTrackedProductRepository trackedRepository;
    private FirestoreStoreProductRepository storeProductRepository;
    private FirestoreProductRepository productRepository;

    @BeforeEach
    void setUp() {
        FirestoreSequenceService sequenceService = new FirestoreSequenceService(null);
        productRepository = new FirestoreProductRepository(null, sequenceService);
        storeProductRepository = new FirestoreStoreProductRepository(null, sequenceService);
        trackedRepository = new FirestoreTrackedProductRepository(null, sequenceService, storeProductRepository, productRepository);
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
