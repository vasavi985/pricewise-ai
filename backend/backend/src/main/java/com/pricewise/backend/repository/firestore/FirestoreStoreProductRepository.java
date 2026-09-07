package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.StoreProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FirestoreStoreProductRepository implements StoreProductRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreStoreProductRepository.class);
    private static final String COLLECTION_NAME = "storeProducts";

    private final Firestore firestore;
    private final FirestoreSequenceService sequenceService;
    private final Map<Long, StoreProduct> inMemoryStoreProducts = new ConcurrentHashMap<>();

    public FirestoreStoreProductRepository(@Autowired(required = false) Firestore firestore,
                                          FirestoreSequenceService sequenceService) {
        this.firestore = firestore;
        this.sequenceService = sequenceService;
    }

    @Override
    public List<StoreProduct> findAll() {
        if (firestore == null) {
            List<StoreProduct> list = new ArrayList<>(inMemoryStoreProducts.values());
            list.sort(Comparator.comparing(sp -> sp.getId() != null ? sp.getId() : 0L));
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<StoreProduct> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                StoreProduct sp = fromSnapshot(doc);
                if (sp != null && sp.getId() != null) {
                    list.add(sp);
                    inMemoryStoreProducts.put(sp.getId(), sp);
                }
            }
            list.sort(Comparator.comparing(sp -> sp.getId() != null ? sp.getId() : 0L));
            return list;
        } catch (Exception e) {
            log.error("Failed to fetch store products from Firestore: {}", e.getMessage());
            List<StoreProduct> list = new ArrayList<>(inMemoryStoreProducts.values());
            list.sort(Comparator.comparing(sp -> sp.getId() != null ? sp.getId() : 0L));
            return list;
        }
    }

    @Override
    public Optional<StoreProduct> findById(Long id) {
        if (id == null) return Optional.empty();

        if (firestore == null) {
            return Optional.ofNullable(inMemoryStoreProducts.get(id));
        }

        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (doc.exists()) {
                StoreProduct sp = fromSnapshot(doc);
                if (sp != null) {
                    inMemoryStoreProducts.put(sp.getId(), sp);
                    return Optional.of(sp);
                }
            }
            return Optional.ofNullable(inMemoryStoreProducts.get(id));
        } catch (Exception e) {
            log.error("Failed to find store product [{}] in Firestore: {}", id, e.getMessage());
            return Optional.ofNullable(inMemoryStoreProducts.get(id));
        }
    }

    @Override
    public StoreProduct save(StoreProduct storeProduct) {
        if (storeProduct == null) return null;

        if (storeProduct.getId() == null) {
            long newId = sequenceService.getNextSequence(COLLECTION_NAME);
            storeProduct.setId(newId);
        } else {
            sequenceService.ensureAtLeast(COLLECTION_NAME, storeProduct.getId());
        }

        if (storeProduct.getLastCheckedAt() == null) {
            storeProduct.setLastCheckedAt(LocalDateTime.now());
        }
        if (storeProduct.getProduct() != null && storeProduct.getProductId() == null) {
            storeProduct.setProductId(storeProduct.getProduct().getId());
        }

        inMemoryStoreProducts.put(storeProduct.getId(), storeProduct);

        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME)
                        .document(String.valueOf(storeProduct.getId()))
                        .set(toMap(storeProduct), SetOptions.merge())
                        .get();
            } catch (Exception e) {
                log.error("Failed to persist store product [{}] to Firestore: {}", storeProduct.getId(), e.getMessage());
            }
        }

        return storeProduct;
    }

    @Override
    public List<StoreProduct> findByProductId(Long productId) {
        if (productId == null) return Collections.emptyList();

        if (firestore == null) {
            List<StoreProduct> matches = new ArrayList<>();
            for (StoreProduct sp : inMemoryStoreProducts.values()) {
                if (productId.equals(sp.getProductId())) {
                    matches.add(sp);
                }
            }
            return matches;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("productId", productId)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<StoreProduct> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                StoreProduct sp = fromSnapshot(doc);
                if (sp != null) {
                    list.add(sp);
                    inMemoryStoreProducts.put(sp.getId(), sp);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to find store products for productId [{}] in Firestore: {}", productId, e.getMessage());
            List<StoreProduct> matches = new ArrayList<>();
            for (StoreProduct sp : inMemoryStoreProducts.values()) {
                if (productId.equals(sp.getProductId())) {
                    matches.add(sp);
                }
            }
            return matches;
        }
    }

    @Override
    public Optional<StoreProduct> findByProductIdAndStore(Long productId, String store) {
        if (productId == null || store == null) return Optional.empty();

        List<StoreProduct> list = findByProductId(productId);
        for (StoreProduct sp : list) {
            if (store.equalsIgnoreCase(sp.getStore())) {
                return Optional.of(sp);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoreProduct> findByStoreAndStoreProductId(String store, String storeProductId) {
        if (store == null || storeProductId == null) return Optional.empty();

        if (firestore == null) {
            for (StoreProduct sp : inMemoryStoreProducts.values()) {
                if (store.equalsIgnoreCase(sp.getStore()) && storeProductId.equalsIgnoreCase(sp.getStoreProductId())) {
                    return Optional.of(sp);
                }
            }
            return Optional.empty();
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("store", store)
                    .whereEqualTo("storeProductId", storeProductId)
                    .limit(1)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            if (!docs.isEmpty()) {
                StoreProduct sp = fromSnapshot(docs.get(0));
                if (sp != null) {
                    inMemoryStoreProducts.put(sp.getId(), sp);
                    return Optional.of(sp);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find store product by store/id in Firestore: {}", e.getMessage());
        }

        for (StoreProduct sp : inMemoryStoreProducts.values()) {
            if (store.equalsIgnoreCase(sp.getStore()) && storeProductId.equalsIgnoreCase(sp.getStoreProductId())) {
                return Optional.of(sp);
            }
        }

        return Optional.empty();
    }

    @Override
    public long count() {
        if (firestore == null) {
            return inMemoryStoreProducts.size();
        }
        try {
            return firestore.collection(COLLECTION_NAME).get().get().size();
        } catch (Exception e) {
            return inMemoryStoreProducts.size();
        }
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        inMemoryStoreProducts.remove(id);
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME).document(String.valueOf(id)).delete().get();
            } catch (Exception e) {
                log.error("Failed to delete store product [{}] from Firestore: {}", id, e.getMessage());
            }
        }
    }

    private Map<String, Object> toMap(StoreProduct sp) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", sp.getId());
        map.put("productId", sp.getProductId());
        map.put("store", sp.getStore());
        map.put("storeProductId", sp.getStoreProductId());
        map.put("title", sp.getTitle());
        map.put("productUrl", sp.getProductUrl());
        map.put("imageUrl", sp.getImageUrl());
        map.put("currentPrice", sp.getCurrentPrice());
        map.put("currency", sp.getCurrency());
        map.put("availability", sp.getAvailability());
        map.put("status", sp.getStatus());
        map.put("lastCheckedAt", sp.getLastCheckedAt() != null ? sp.getLastCheckedAt().toString() : LocalDateTime.now().toString());
        return map;
    }

    private StoreProduct fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        StoreProduct sp = new StoreProduct();
        Long id = doc.getLong("id");
        if (id == null) {
            try { id = Long.parseLong(doc.getId()); } catch (Exception ignored) {}
        }
        sp.setId(id);
        sp.setProductId(doc.getLong("productId"));
        sp.setStore(doc.getString("store"));
        sp.setStoreProductId(doc.getString("storeProductId"));
        sp.setTitle(doc.getString("title"));
        sp.setProductUrl(doc.getString("productUrl"));
        sp.setImageUrl(doc.getString("imageUrl"));
        sp.setCurrentPrice(doc.getDouble("currentPrice"));
        sp.setCurrency(doc.getString("currency"));
        sp.setAvailability(doc.getString("availability"));
        sp.setStatus(doc.getString("status"));

        String lastCheckedStr = doc.getString("lastCheckedAt");
        if (lastCheckedStr != null) {
            try { sp.setLastCheckedAt(LocalDateTime.parse(lastCheckedStr)); } catch (Exception ignored) {}
        }
        return sp;
    }
}
