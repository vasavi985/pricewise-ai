package com.pricewise.backend.repository;

import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.firestore.FirestoreSequenceService;
import com.pricewise.backend.repository.firestore.FirestoreStoreProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreStoreProductRepositoryTest {

    private FirestoreStoreProductRepository repository;

    @BeforeEach
    void setUp() {
        FirestoreSequenceService sequenceService = new FirestoreSequenceService(null);
        repository = new FirestoreStoreProductRepository(null, sequenceService);
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
}
