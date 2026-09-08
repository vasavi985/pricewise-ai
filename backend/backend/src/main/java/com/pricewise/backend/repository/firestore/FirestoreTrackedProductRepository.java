package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import com.pricewise.backend.repository.TrackedProductRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FirestoreTrackedProductRepository implements TrackedProductRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreTrackedProductRepository.class);
    private static final String COLLECTION_NAME = "trackedProducts";

    private final Firestore firestore;
    private final DistributedIdGenerator idGenerator;
    private final StoreProductRepository storeProductRepository;
    private final ProductRepository productRepository;
    private final Map<Long, TrackedProduct> inMemoryTracked = new ConcurrentHashMap<>();

    @Autowired
    public FirestoreTrackedProductRepository(@Autowired(required = false) Firestore firestore,
                                            DistributedIdGenerator idGenerator,
                                            @Lazy StoreProductRepository storeProductRepository,
                                            @Lazy ProductRepository productRepository) {
        this.firestore = firestore;
        this.idGenerator = idGenerator != null ? idGenerator : new DistributedIdGenerator();
        this.storeProductRepository = storeProductRepository;
        this.productRepository = productRepository;
    }

    public FirestoreTrackedProductRepository(Firestore firestore,
                                            FirestoreSequenceService sequenceService,
                                            StoreProductRepository storeProductRepository,
                                            ProductRepository productRepository) {
        this(firestore, new DistributedIdGenerator(), storeProductRepository, productRepository);
    }

    @Override
    public List<TrackedProduct> findAll() {
        if (firestore == null) {
            List<TrackedProduct> list = new ArrayList<>(inMemoryTracked.values());
            for (TrackedProduct tp : list) {
                hydrate(tp);
            }
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<TrackedProduct> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                TrackedProduct tp = fromSnapshot(doc);
                if (tp != null && tp.getId() != null) {
                    hydrate(tp);
                    list.add(tp);
                    inMemoryTracked.put(tp.getId(), tp);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to fetch all tracked products from Firestore: {}", e.getMessage());
            List<TrackedProduct> list = new ArrayList<>(inMemoryTracked.values());
            for (TrackedProduct tp : list) {
                hydrate(tp);
            }
            return list;
        }
    }

    @Override
    public Optional<TrackedProduct> findById(Long id) {
        if (id == null) return Optional.empty();

        if (firestore == null) {
            TrackedProduct tp = inMemoryTracked.get(id);
            if (tp != null) hydrate(tp);
            return Optional.ofNullable(tp);
        }

        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (doc.exists()) {
                TrackedProduct tp = fromSnapshot(doc);
                if (tp != null) {
                    hydrate(tp);
                    inMemoryTracked.put(tp.getId(), tp);
                    return Optional.of(tp);
                }
            }
            TrackedProduct local = inMemoryTracked.get(id);
            if (local != null) hydrate(local);
            return Optional.ofNullable(local);
        } catch (Exception e) {
            log.error("Failed to find tracked product [{}] in Firestore: {}", id, e.getMessage());
            TrackedProduct local = inMemoryTracked.get(id);
            if (local != null) hydrate(local);
            return Optional.ofNullable(local);
        }
    }

    @Override
    public TrackedProduct save(TrackedProduct trackedProduct) {
        if (trackedProduct == null) return null;

        if (trackedProduct.getId() == null) {
            long newId = idGenerator.nextId();
            trackedProduct.setId(newId);
        }

        if (trackedProduct.getCreatedAt() == null) {
            trackedProduct.setCreatedAt(LocalDateTime.now());
        }
        if (trackedProduct.getLastCheckedAt() == null) {
            trackedProduct.setLastCheckedAt(LocalDateTime.now());
        }
        if (trackedProduct.getStoreProduct() != null && trackedProduct.getStoreProductId() == null) {
            trackedProduct.setStoreProductId(trackedProduct.getStoreProduct().getId());
        }

        inMemoryTracked.put(trackedProduct.getId(), trackedProduct);

        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME)
                        .document(String.valueOf(trackedProduct.getId()))
                        .set(toMap(trackedProduct), SetOptions.merge())
                        .get();
            } catch (Exception e) {
                log.error("Failed to persist tracked product [{}] to Firestore: {}", trackedProduct.getId(), e.getMessage());
            }
        }

        hydrate(trackedProduct);
        return trackedProduct;
    }

    @Override
    public List<TrackedProduct> findByActiveTrue() {
        if (firestore == null) {
            List<TrackedProduct> list = new ArrayList<>();
            for (TrackedProduct tp : inMemoryTracked.values()) {
                if (Boolean.TRUE.equals(tp.getActive())) {
                    hydrate(tp);
                    list.add(tp);
                }
            }
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("active", true)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<TrackedProduct> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                TrackedProduct tp = fromSnapshot(doc);
                if (tp != null) {
                    hydrate(tp);
                    list.add(tp);
                    inMemoryTracked.put(tp.getId(), tp);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to query active tracked products from Firestore: {}", e.getMessage());
            List<TrackedProduct> list = new ArrayList<>();
            for (TrackedProduct tp : inMemoryTracked.values()) {
                if (Boolean.TRUE.equals(tp.getActive())) {
                    hydrate(tp);
                    list.add(tp);
                }
            }
            return list;
        }
    }

    @Override
    public List<TrackedProduct> findByUserIdAndActiveTrue(String userId) {
        if (userId == null) return Collections.emptyList();

        if (firestore == null) {
            List<TrackedProduct> list = new ArrayList<>();
            for (TrackedProduct tp : inMemoryTracked.values()) {
                if (userId.equalsIgnoreCase(tp.getUserId()) && Boolean.TRUE.equals(tp.getActive())) {
                    hydrate(tp);
                    list.add(tp);
                }
            }
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("active", true)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<TrackedProduct> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                TrackedProduct tp = fromSnapshot(doc);
                if (tp != null) {
                    hydrate(tp);
                    list.add(tp);
                    inMemoryTracked.put(tp.getId(), tp);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to query tracked products by user in Firestore: {}", e.getMessage());
            List<TrackedProduct> list = new ArrayList<>();
            for (TrackedProduct tp : inMemoryTracked.values()) {
                if (userId.equalsIgnoreCase(tp.getUserId()) && Boolean.TRUE.equals(tp.getActive())) {
                    hydrate(tp);
                    list.add(tp);
                }
            }
            return list;
        }
    }

    @Override
    public Optional<TrackedProduct> findByStoreProductIdAndUserIdAndActiveTrue(Long storeProductId, String userId) {
        if (storeProductId == null || userId == null) return Optional.empty();

        if (firestore == null) {
            for (TrackedProduct tp : inMemoryTracked.values()) {
                if (storeProductId.equals(tp.getStoreProductId()) && userId.equalsIgnoreCase(tp.getUserId()) && Boolean.TRUE.equals(tp.getActive())) {
                    hydrate(tp);
                    return Optional.of(tp);
                }
            }
            return Optional.empty();
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("storeProductId", storeProductId)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("active", true)
                    .limit(1)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            if (!docs.isEmpty()) {
                TrackedProduct tp = fromSnapshot(docs.get(0));
                if (tp != null) {
                    hydrate(tp);
                    inMemoryTracked.put(tp.getId(), tp);
                    return Optional.of(tp);
                }
            }
        } catch (Exception e) {
            log.error("Failed to query tracked product by storeProductId/user in Firestore: {}", e.getMessage());
        }

        for (TrackedProduct tp : inMemoryTracked.values()) {
            if (storeProductId.equals(tp.getStoreProductId()) && userId.equalsIgnoreCase(tp.getUserId()) && Boolean.TRUE.equals(tp.getActive())) {
                hydrate(tp);
                return Optional.of(tp);
            }
        }

        return Optional.empty();
    }

    @Override
    public long count() {
        if (firestore == null) {
            return inMemoryTracked.size();
        }
        try {
            return firestore.collection(COLLECTION_NAME).get().get().size();
        } catch (Exception e) {
            return inMemoryTracked.size();
        }
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        inMemoryTracked.remove(id);
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME).document(String.valueOf(id)).delete().get();
            } catch (Exception e) {
                log.error("Failed to delete tracked product [{}] from Firestore: {}", id, e.getMessage());
            }
        }
    }

    private void hydrate(TrackedProduct tp) {
        if (tp == null) return;
        if (tp.getStoreProduct() == null && tp.getStoreProductId() != null && storeProductRepository != null) {
            storeProductRepository.findById(tp.getStoreProductId()).ifPresent(sp -> {
                if (sp.getProduct() == null && sp.getProductId() != null && productRepository != null) {
                    productRepository.findById(sp.getProductId()).ifPresent(sp::setProduct);
                }
                tp.setStoreProduct(sp);
            });
        } else if (tp.getStoreProduct() != null && tp.getStoreProduct().getProduct() == null && tp.getStoreProduct().getProductId() != null && productRepository != null) {
            productRepository.findById(tp.getStoreProduct().getProductId()).ifPresent(p -> tp.getStoreProduct().setProduct(p));
        }
    }

    private Map<String, Object> toMap(TrackedProduct tp) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", tp.getId());
        map.put("storeProductId", tp.getStoreProductId());
        map.put("userId", tp.getUserId());
        map.put("userEmail", tp.getUserEmail());
        map.put("targetPrice", tp.getTargetPrice());
        map.put("targetDropPercentage", tp.getTargetDropPercentage());
        map.put("initialPrice", tp.getInitialPrice());
        map.put("lastNotifiedPrice", tp.getLastNotifiedPrice());
        map.put("active", tp.getActive());
        map.put("createdAt", tp.getCreatedAt() != null ? tp.getCreatedAt().toString() : LocalDateTime.now().toString());
        map.put("lastCheckedAt", tp.getLastCheckedAt() != null ? tp.getLastCheckedAt().toString() : LocalDateTime.now().toString());
        return map;
    }

    private TrackedProduct fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        TrackedProduct tp = new TrackedProduct();
        Long id = doc.getLong("id");
        if (id == null) {
            try { id = Long.parseLong(doc.getId()); } catch (Exception ignored) {}
        }
        tp.setId(id);
        tp.setStoreProductId(doc.getLong("storeProductId"));
        tp.setUserId(doc.getString("userId"));
        tp.setUserEmail(doc.getString("userEmail"));
        tp.setTargetPrice(doc.getDouble("targetPrice"));
        tp.setTargetDropPercentage(doc.getDouble("targetDropPercentage"));
        tp.setInitialPrice(doc.getDouble("initialPrice"));
        tp.setLastNotifiedPrice(doc.getDouble("lastNotifiedPrice"));
        tp.setActive(doc.getBoolean("active"));

        String createdStr = doc.getString("createdAt");
        if (createdStr != null) {
            try { tp.setCreatedAt(LocalDateTime.parse(createdStr)); } catch (Exception ignored) {}
        }
        String lastCheckedStr = doc.getString("lastCheckedAt");
        if (lastCheckedStr != null) {
            try { tp.setLastCheckedAt(LocalDateTime.parse(lastCheckedStr)); } catch (Exception ignored) {}
        }
        return tp;
    }
}
