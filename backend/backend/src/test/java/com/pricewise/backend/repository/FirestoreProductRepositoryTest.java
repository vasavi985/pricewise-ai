package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import com.pricewise.backend.repository.firestore.FirestoreProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
}
