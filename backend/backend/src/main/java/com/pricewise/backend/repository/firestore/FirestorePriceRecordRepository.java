package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FirestorePriceRecordRepository implements PriceRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestorePriceRecordRepository.class);
    private static final String COLLECTION_NAME = "priceRecords";

    private final Firestore firestore;
    private final DistributedIdGenerator idGenerator;
    private final Map<Long, PriceRecord> inMemoryRecords = new ConcurrentHashMap<>();
    private final Map<Long, PriceRecord> latestByStoreProductId = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();
    private volatile boolean cacheInitialized = false;

    @Autowired
    public FirestorePriceRecordRepository(@Autowired(required = false) Firestore firestore,
                                          DistributedIdGenerator idGenerator) {
        this.firestore = firestore;
        this.idGenerator = idGenerator != null ? idGenerator : new DistributedIdGenerator();
    }

    public FirestorePriceRecordRepository(Firestore firestore, FirestoreSequenceService sequenceService) {
        this(firestore, new DistributedIdGenerator());
    }

    private void ensureCacheInitialized() {
        if (firestore == null || cacheInitialized) {
            return;
        }

        synchronized (cacheLock) {
            if (!cacheInitialized) {
                log.info("Initializing in-memory price records cache from Firestore...");
                try {
                    ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
                    List<QueryDocumentSnapshot> docs = future.get().getDocuments();
                    for (DocumentSnapshot doc : docs) {
                        PriceRecord r = fromSnapshot(doc);
                        if (r != null && r.getId() != null) {
                            inMemoryRecords.put(r.getId(), r);
                            updateLatestCache(r);
                        }
                    }
                    log.info("Loaded {} price records from Firestore into in-memory cache ({} unique storeProducts).",
                            inMemoryRecords.size(), latestByStoreProductId.size());
                } catch (Exception e) {
                    log.error("Failed to initialize price records cache from Firestore: {}", e.getMessage());
                } finally {
                    cacheInitialized = true;
                }
            }
        }
    }

    private void updateLatestCache(PriceRecord r) {
        if (r == null || r.getStoreProductId() == null) return;
        latestByStoreProductId.compute(r.getStoreProductId(), (spId, existing) -> {
            if (existing == null) {
                return r;
            }
            LocalDateTime existingTime = existing.getCheckedAt() != null ? existing.getCheckedAt() : LocalDateTime.MIN;
            LocalDateTime newTime = r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN;
            return (newTime.isAfter(existingTime) || newTime.isEqual(existingTime)) ? r : existing;
        });
    }

    public boolean isCacheInitialized() {
        return cacheInitialized;
    }

    public void resetCacheForTesting() {
        synchronized (cacheLock) {
            inMemoryRecords.clear();
            latestByStoreProductId.clear();
            cacheInitialized = false;
        }
    }

    @Override
    public List<PriceRecord> findAll() {
        ensureCacheInitialized();
        List<PriceRecord> list = new ArrayList<>(inMemoryRecords.values());
        list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
        return list;
    }

    @Override
    public Optional<PriceRecord> findById(Long id) {
        if (id == null) return Optional.empty();

        ensureCacheInitialized();
        PriceRecord cached = inMemoryRecords.get(id);
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
                PriceRecord r = fromSnapshot(doc);
                if (r != null) {
                    inMemoryRecords.put(r.getId(), r);
                    updateLatestCache(r);
                    return Optional.of(r);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to find price record [{}] in Firestore: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public PriceRecord save(PriceRecord record) {
        if (record == null) return null;

        if (record.getId() == null) {
            long newId = idGenerator.nextId();
            record.setId(newId);
        }

        if (record.getCheckedAt() == null) {
            record.setCheckedAt(LocalDateTime.now());
        }

        if (record.getStoreProduct() != null) {
            if (record.getStoreProductId() == null) {
                record.setStoreProductId(record.getStoreProduct().getId());
            }
            if (record.getProductId() == null && record.getStoreProduct().getProduct() != null) {
                record.setProductId(record.getStoreProduct().getProduct().getId());
            } else if (record.getProductId() == null && record.getStoreProduct().getProductId() != null) {
                record.setProductId(record.getStoreProduct().getProductId());
            }
            if (record.getStore() == null) {
                record.setStore(record.getStoreProduct().getStore());
            }
        }

        inMemoryRecords.put(record.getId(), record);
        updateLatestCache(record);

        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME)
                        .document(String.valueOf(record.getId()))
                        .set(toMap(record), SetOptions.merge())
                        .get();
            } catch (Exception e) {
                log.error("Failed to persist price record [{}] to Firestore: {}", record.getId(), e.getMessage());
            }
        }

        return record;
    }

    @Override
    public List<PriceRecord> findByStoreProductIdOrderByCheckedAtAsc(Long storeProductId) {
        if (storeProductId == null) return Collections.emptyList();

        ensureCacheInitialized();
        List<PriceRecord> list = new ArrayList<>();
        for (PriceRecord r : inMemoryRecords.values()) {
            if (storeProductId.equals(r.getStoreProductId())) {
                list.add(r);
            }
        }
        list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
        return list;
    }

    @Override
    public Optional<PriceRecord> findFirstByStoreProductIdOrderByCheckedAtDesc(Long storeProductId) {
        if (storeProductId == null) return Optional.empty();

        ensureCacheInitialized();

        PriceRecord cached = latestByStoreProductId.get(storeProductId);
        if (cached != null) {
            return Optional.of(cached);
        }

        List<PriceRecord> inMemMatches = new ArrayList<>();
        for (PriceRecord r : inMemoryRecords.values()) {
            if (storeProductId.equals(r.getStoreProductId())) {
                inMemMatches.add(r);
            }
        }
        if (!inMemMatches.isEmpty()) {
            inMemMatches.sort((a, b) -> {
                LocalDateTime ta = a.getCheckedAt() != null ? a.getCheckedAt() : LocalDateTime.MIN;
                LocalDateTime tb = b.getCheckedAt() != null ? b.getCheckedAt() : LocalDateTime.MIN;
                return tb.compareTo(ta);
            });
            updateLatestCache(inMemMatches.get(0));
            return Optional.of(inMemMatches.get(0));
        }

        return Optional.empty();
    }

    @Override
    public List<PriceRecord> findByProductIdOrderByCheckedAtAsc(Long productId) {
        if (productId == null) return Collections.emptyList();

        ensureCacheInitialized();
        List<PriceRecord> list = new ArrayList<>();
        for (PriceRecord r : inMemoryRecords.values()) {
            if (productId.equals(r.getProductId())) {
                list.add(r);
            }
        }
        list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
        return list;
    }

    @Override
    public List<PriceRecord> findByProductIdOrderByCheckedAtDesc(Long productId) {
        if (productId == null) return Collections.emptyList();

        ensureCacheInitialized();
        List<PriceRecord> list = new ArrayList<>();
        for (PriceRecord r : inMemoryRecords.values()) {
            if (productId.equals(r.getProductId())) {
                list.add(r);
            }
        }
        list.sort((a, b) -> {
            LocalDateTime ta = a.getCheckedAt() != null ? a.getCheckedAt() : LocalDateTime.MIN;
            LocalDateTime tb = b.getCheckedAt() != null ? b.getCheckedAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });
        return list;
    }

    @Override
    public long count() {
        ensureCacheInitialized();
        return inMemoryRecords.size();
    }

    private Map<String, Object> toMap(PriceRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("storeProductId", record.getStoreProductId());
        map.put("productId", record.getProductId());
        map.put("store", record.getStore());
        map.put("price", record.getPrice());
        map.put("currency", record.getCurrency());
        map.put("availability", record.getAvailability());
        map.put("status", record.getStatus());
        map.put("source", record.getSource());
        map.put("checkedAt", record.getCheckedAt() != null ? record.getCheckedAt().toString() : LocalDateTime.now().toString());
        return map;
    }

    private PriceRecord fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        PriceRecord r = new PriceRecord();
        Long id = doc.getLong("id");
        if (id == null) {
            try { id = Long.parseLong(doc.getId()); } catch (Exception ignored) {}
        }
        r.setId(id);
        r.setStoreProductId(doc.getLong("storeProductId"));
        r.setProductId(doc.getLong("productId"));
        r.setStore(doc.getString("store"));
        r.setPrice(doc.getDouble("price"));
        r.setCurrency(doc.getString("currency"));
        r.setAvailability(doc.getString("availability"));
        r.setStatus(doc.getString("status"));
        r.setSource(doc.getString("source"));

        String checkedAtStr = doc.getString("checkedAt");
        if (checkedAtStr != null) {
            try { r.setCheckedAt(LocalDateTime.parse(checkedAtStr)); } catch (Exception ignored) {}
        }
        return r;
    }
}
