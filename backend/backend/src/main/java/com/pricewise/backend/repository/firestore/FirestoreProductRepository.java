package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FirestoreProductRepository implements ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreProductRepository.class);
    private static final String COLLECTION_NAME = "products";

    private final Firestore firestore;
    private final DistributedIdGenerator idGenerator;
    private final Map<Long, Product> inMemoryProducts = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();
    private volatile boolean cacheInitialized = false;

    @Autowired
    public FirestoreProductRepository(@Autowired(required = false) Firestore firestore,
                                    DistributedIdGenerator idGenerator) {
        this.firestore = firestore;
        this.idGenerator = idGenerator != null ? idGenerator : new DistributedIdGenerator();
    }

    public FirestoreProductRepository(Firestore firestore, FirestoreSequenceService sequenceService) {
        this(firestore, new DistributedIdGenerator());
    }

    private void ensureCacheInitialized() {
        if (firestore == null || cacheInitialized) {
            return;
        }

        synchronized (cacheLock) {
            if (!cacheInitialized) {
                log.info("Initializing in-memory product cache from Firestore...");
                try {
                    ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
                    List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                    for (DocumentSnapshot doc : docs) {
                        Product p = fromSnapshot(doc);
                        if (p != null && p.getId() != null) {
                            inMemoryProducts.put(p.getId(), p);
                        }
                    }
                    log.info("Loaded {} products from Firestore into in-memory cache.", inMemoryProducts.size());
                } catch (Exception e) {
                    log.error("Failed to initialize product cache from Firestore: {}", e.getMessage());
                } finally {
                    cacheInitialized = true;
                }
            }
        }
    }

    public boolean isCacheInitialized() {
        return cacheInitialized;
    }

    public void resetCacheForTesting() {
        synchronized (cacheLock) {
            inMemoryProducts.clear();
            cacheInitialized = false;
        }
    }

    @Override
    public List<Product> findAll() {
        ensureCacheInitialized();
        List<Product> list = new ArrayList<>(inMemoryProducts.values());
        list.sort(Comparator.comparing(p -> p.getId() != null ? p.getId() : 0L));
        return list;
    }

    @Override
    public Optional<Product> findById(Long id) {
        if (id == null) return Optional.empty();

        ensureCacheInitialized();
        Product cached = inMemoryProducts.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        if (firestore == null) {
            return Optional.empty();
        }

        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (doc.exists()) {
                Product p = fromSnapshot(doc);
                if (p != null) {
                    inMemoryProducts.put(p.getId(), p);
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to find product by id [{}] in Firestore: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Product save(Product product) {
        if (product == null) return null;

        if (product.getId() == null) {
            long newId = idGenerator.nextId();
            product.setId(newId);
        }

        if (product.getCreatedAt() == null) {
            product.setCreatedAt(LocalDateTime.now());
        }
        product.setUpdatedAt(LocalDateTime.now());
        if (product.getCanonicalName() == null && product.getProductName() != null) {
            product.setCanonicalName(product.getProductName());
        }

        inMemoryProducts.put(product.getId(), product);

        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME)
                        .document(String.valueOf(product.getId()))
                        .set(toMap(product), SetOptions.merge())
                        .get();
            } catch (Exception e) {
                log.error("Failed to persist product [{}] to Firestore: {}", product.getId(), e.getMessage());
            }
        }

        return product;
    }

    @Override
    public List<Product> searchProducts(String query) {
        ensureCacheInitialized();
        if (query == null || query.trim().isEmpty()) {
            return findAll();
        }

        String lower = query.trim().toLowerCase();
        List<Product> matches = new ArrayList<>();

        for (Product p : inMemoryProducts.values()) {
            boolean matchName = p.getProductName() != null && p.getProductName().toLowerCase().contains(lower);
            boolean matchCanonical = p.getCanonicalName() != null && p.getCanonicalName().toLowerCase().contains(lower);
            boolean matchBrand = p.getBrand() != null && p.getBrand().toLowerCase().contains(lower);

            if (matchName || matchCanonical || matchBrand) {
                matches.add(p);
            }
        }
        matches.sort(Comparator.comparing(p -> p.getId() != null ? p.getId() : 0L));

        return matches;
    }

    @Override
    public Optional<Product> findByCanonicalNameIgnoreCase(String canonicalName) {
        if (canonicalName == null || canonicalName.trim().isEmpty()) return Optional.empty();
        ensureCacheInitialized();
        String target = canonicalName.trim().toLowerCase();

        for (Product p : inMemoryProducts.values()) {
            if (p.getCanonicalName() != null && p.getCanonicalName().trim().equalsIgnoreCase(target)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Product> findByProductNameIgnoreCase(String productName) {
        if (productName == null || productName.trim().isEmpty()) return Optional.empty();
        ensureCacheInitialized();
        String target = productName.trim().toLowerCase();

        for (Product p : inMemoryProducts.values()) {
            if (p.getProductName() != null && p.getProductName().trim().equalsIgnoreCase(target)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @Override
    public long count() {
        ensureCacheInitialized();
        return inMemoryProducts.size();
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        inMemoryProducts.remove(id);
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME).document(String.valueOf(id)).delete().get();
            } catch (Exception e) {
                log.error("Failed to delete product [{}] from Firestore: {}", id, e.getMessage());
            }
        }
    }

    private Map<String, Object> toMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("productName", product.getProductName());
        map.put("canonicalName", product.getCanonicalName());
        map.put("brand", product.getBrand());
        map.put("model", product.getModel());
        map.put("category", product.getCategory());
        map.put("description", product.getDescription());
        map.put("imageUrl", product.getImageUrl());
        map.put("rating", product.getRating());
        map.put("amazonPrice", product.getAmazonPrice());
        map.put("flipkartPrice", product.getFlipkartPrice());
        map.put("cromaPrice", product.getCromaPrice());
        map.put("createdAt", product.getCreatedAt() != null ? product.getCreatedAt().toString() : LocalDateTime.now().toString());
        map.put("updatedAt", product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : LocalDateTime.now().toString());
        return map;
    }

    private Product fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Product p = new Product();
        Long id = doc.getLong("id");
        if (id == null) {
            try {
                id = Long.parseLong(doc.getId());
            } catch (Exception ignored) {}
        }
        p.setId(id);
        p.setProductName(doc.getString("productName"));
        p.setCanonicalName(doc.getString("canonicalName"));
        p.setBrand(doc.getString("brand"));
        p.setModel(doc.getString("model"));
        p.setCategory(doc.getString("category"));
        p.setDescription(doc.getString("description"));
        p.setImageUrl(doc.getString("imageUrl"));
        p.setRating(doc.getDouble("rating"));
        p.setAmazonPrice(doc.getDouble("amazonPrice"));
        p.setFlipkartPrice(doc.getDouble("flipkartPrice"));
        p.setCromaPrice(doc.getDouble("cromaPrice"));

        String createdStr = doc.getString("createdAt");
        if (createdStr != null) {
            try { p.setCreatedAt(LocalDateTime.parse(createdStr)); } catch (Exception ignored) {}
        }
        String updatedStr = doc.getString("updatedAt");
        if (updatedStr != null) {
            try { p.setUpdatedAt(LocalDateTime.parse(updatedStr)); } catch (Exception ignored) {}
        }
        return p;
    }
}
