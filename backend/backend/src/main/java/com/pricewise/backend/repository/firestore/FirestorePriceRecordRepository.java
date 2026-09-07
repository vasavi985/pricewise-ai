package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.repository.PriceRecordRepository;
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
    private final FirestoreSequenceService sequenceService;
    private final Map<Long, PriceRecord> inMemoryRecords = new ConcurrentHashMap<>();

    public FirestorePriceRecordRepository(@Autowired(required = false) Firestore firestore,
                                         FirestoreSequenceService sequenceService) {
        this.firestore = firestore;
        this.sequenceService = sequenceService;
    }

    @Override
    public List<PriceRecord> findAll() {
        if (firestore == null) {
            List<PriceRecord> list = new ArrayList<>(inMemoryRecords.values());
            list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<PriceRecord> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                PriceRecord r = fromSnapshot(doc);
                if (r != null && r.getId() != null) {
                    list.add(r);
                    inMemoryRecords.put(r.getId(), r);
                }
            }
            list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
            return list;
        } catch (Exception e) {
            log.error("Failed to fetch price records from Firestore: {}", e.getMessage());
            List<PriceRecord> list = new ArrayList<>(inMemoryRecords.values());
            list.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
            return list;
        }
    }

    @Override
    public Optional<PriceRecord> findById(Long id) {
        if (id == null) return Optional.empty();

        if (firestore == null) {
            return Optional.ofNullable(inMemoryRecords.get(id));
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
                    return Optional.of(r);
                }
            }
            return Optional.ofNullable(inMemoryRecords.get(id));
        } catch (Exception e) {
            log.error("Failed to find price record [{}] in Firestore: {}", id, e.getMessage());
            return Optional.ofNullable(inMemoryRecords.get(id));
        }
    }

    @Override
    public PriceRecord save(PriceRecord record) {
        if (record == null) return null;

        if (record.getId() == null) {
            long newId = sequenceService.getNextSequence(COLLECTION_NAME);
            record.setId(newId);
        } else {
            sequenceService.ensureAtLeast(COLLECTION_NAME, record.getId());
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

        List<PriceRecord> records = fetchByStoreProductId(storeProductId);
        records.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
        return records;
    }

    @Override
    public Optional<PriceRecord> findFirstByStoreProductIdOrderByCheckedAtDesc(Long storeProductId) {
        if (storeProductId == null) return Optional.empty();

        List<PriceRecord> records = fetchByStoreProductId(storeProductId);
        if (records.isEmpty()) return Optional.empty();

        records.sort((a, b) -> {
            LocalDateTime ta = a.getCheckedAt() != null ? a.getCheckedAt() : LocalDateTime.MIN;
            LocalDateTime tb = b.getCheckedAt() != null ? b.getCheckedAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });

        return Optional.of(records.get(0));
    }

    @Override
    public List<PriceRecord> findByProductIdOrderByCheckedAtAsc(Long productId) {
        if (productId == null) return Collections.emptyList();

        List<PriceRecord> records = fetchByProductId(productId);
        records.sort(Comparator.comparing(r -> r.getCheckedAt() != null ? r.getCheckedAt() : LocalDateTime.MIN));
        return records;
    }

    @Override
    public List<PriceRecord> findByProductIdOrderByCheckedAtDesc(Long productId) {
        if (productId == null) return Collections.emptyList();

        List<PriceRecord> records = fetchByProductId(productId);
        records.sort((a, b) -> {
            LocalDateTime ta = a.getCheckedAt() != null ? a.getCheckedAt() : LocalDateTime.MIN;
            LocalDateTime tb = b.getCheckedAt() != null ? b.getCheckedAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });
        return records;
    }

    @Override
    public long count() {
        if (firestore == null) {
            return inMemoryRecords.size();
        }
        try {
            return firestore.collection(COLLECTION_NAME).get().get().size();
        } catch (Exception e) {
            return inMemoryRecords.size();
        }
    }

    private List<PriceRecord> fetchByStoreProductId(Long storeProductId) {
        if (firestore == null) {
            List<PriceRecord> list = new ArrayList<>();
            for (PriceRecord r : inMemoryRecords.values()) {
                if (storeProductId.equals(r.getStoreProductId())) {
                    list.add(r);
                }
            }
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("storeProductId", storeProductId)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<PriceRecord> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                PriceRecord r = fromSnapshot(doc);
                if (r != null) {
                    list.add(r);
                    inMemoryRecords.put(r.getId(), r);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to query price records by storeProductId [{}] in Firestore: {}", storeProductId, e.getMessage());
            List<PriceRecord> list = new ArrayList<>();
            for (PriceRecord r : inMemoryRecords.values()) {
                if (storeProductId.equals(r.getStoreProductId())) {
                    list.add(r);
                }
            }
            return list;
        }
    }

    private List<PriceRecord> fetchByProductId(Long productId) {
        if (firestore == null) {
            List<PriceRecord> list = new ArrayList<>();
            for (PriceRecord r : inMemoryRecords.values()) {
                if (productId.equals(r.getProductId())) {
                    list.add(r);
                }
            }
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("productId", productId)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<PriceRecord> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                PriceRecord r = fromSnapshot(doc);
                if (r != null) {
                    list.add(r);
                    inMemoryRecords.put(r.getId(), r);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to query price records by productId [{}] in Firestore: {}", productId, e.getMessage());
            List<PriceRecord> list = new ArrayList<>();
            for (PriceRecord r : inMemoryRecords.values()) {
                if (productId.equals(r.getProductId())) {
                    list.add(r);
                }
            }
            return list;
        }
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
