package com.pricewise.backend.repository;

import com.pricewise.backend.entity.Product;
import com.pricewise.backend.repository.firestore.FirestoreProductRepository;
import com.pricewise.backend.repository.firestore.FirestoreSequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreProductRepositoryTest {

    private FirestoreProductRepository repository;

    @BeforeEach
    void setUp() {
        FirestoreSequenceService sequenceService = new FirestoreSequenceService(null);
        repository = new FirestoreProductRepository(null, sequenceService);
    }

    @Test
    void testSaveAndFindById() {
        Product p = new Product();
        p.setCanonicalName("Google Pixel 9");
        p.setBrand("Google");
        p.setCategory("Smartphones");

        Product saved = repository.save(p);

        assertNotNull(saved.getId());
        assertEquals(1L, saved.getId());

        Optional<Product> found = repository.findById(1L);
        assertTrue(found.isPresent());
        assertEquals("Google Pixel 9", found.get().getCanonicalName());
        assertEquals("Google", found.get().getBrand());
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
