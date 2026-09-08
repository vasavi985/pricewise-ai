package com.pricewise.backend.repository;

import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.firestore.FirestoreStoreProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
}
