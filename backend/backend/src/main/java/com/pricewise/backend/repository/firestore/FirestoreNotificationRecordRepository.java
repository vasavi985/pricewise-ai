package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.NotificationRecord;
import com.pricewise.backend.repository.NotificationRecordRepository;
import com.pricewise.backend.util.DistributedIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FirestoreNotificationRecordRepository implements NotificationRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreNotificationRecordRepository.class);
    private static final String COLLECTION_NAME = "notificationRecords";

    private final Firestore firestore;
    private final DistributedIdGenerator idGenerator;
    private final Map<Long, NotificationRecord> inMemoryNotifications = new ConcurrentHashMap<>();

    @Autowired
    public FirestoreNotificationRecordRepository(@Autowired(required = false) Firestore firestore,
                                                DistributedIdGenerator idGenerator) {
        this.firestore = firestore;
        this.idGenerator = idGenerator != null ? idGenerator : new DistributedIdGenerator();
    }

    public FirestoreNotificationRecordRepository(Firestore firestore, FirestoreSequenceService sequenceService) {
        this(firestore, new DistributedIdGenerator());
    }

    @Override
    public List<NotificationRecord> findAll() {
        if (firestore == null) {
            List<NotificationRecord> list = new ArrayList<>(inMemoryNotifications.values());
            list.sort(Comparator.comparing(n -> n.getSentAt() != null ? n.getSentAt() : LocalDateTime.MIN));
            return list;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<NotificationRecord> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                NotificationRecord nr = fromSnapshot(doc);
                if (nr != null && nr.getId() != null) {
                    list.add(nr);
                    inMemoryNotifications.put(nr.getId(), nr);
                }
            }
            list.sort(Comparator.comparing(n -> n.getSentAt() != null ? n.getSentAt() : LocalDateTime.MIN));
            return list;
        } catch (Exception e) {
            log.error("Failed to fetch notification records from Firestore: {}", e.getMessage());
            List<NotificationRecord> list = new ArrayList<>(inMemoryNotifications.values());
            list.sort(Comparator.comparing(n -> n.getSentAt() != null ? n.getSentAt() : LocalDateTime.MIN));
            return list;
        }
    }

    @Override
    public Optional<NotificationRecord> findById(Long id) {
        if (id == null) return Optional.empty();

        if (firestore == null) {
            return Optional.ofNullable(inMemoryNotifications.get(id));
        }

        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION_NAME)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (doc.exists()) {
                NotificationRecord nr = fromSnapshot(doc);
                if (nr != null) {
                    inMemoryNotifications.put(nr.getId(), nr);
                    return Optional.of(nr);
                }
            }
            return Optional.ofNullable(inMemoryNotifications.get(id));
        } catch (Exception e) {
            log.error("Failed to find notification record [{}] in Firestore: {}", id, e.getMessage());
            return Optional.ofNullable(inMemoryNotifications.get(id));
        }
    }

    @Override
    public NotificationRecord save(NotificationRecord record) {
        if (record == null) return null;

        if (record.getId() == null) {
            long newId = idGenerator.nextId();
            record.setId(newId);
        }

        if (record.getSentAt() == null) {
            record.setSentAt(LocalDateTime.now());
        }
        if (record.getTrackedProduct() != null && record.getTrackedProductId() == null) {
            record.setTrackedProductId(record.getTrackedProduct().getId());
        }

        inMemoryNotifications.put(record.getId(), record);

        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_NAME)
                        .document(String.valueOf(record.getId()))
                        .set(toMap(record), SetOptions.merge())
                        .get();
            } catch (Exception e) {
                log.error("Failed to persist notification record [{}] to Firestore: {}", record.getId(), e.getMessage());
            }
        }

        return record;
    }

    @Override
    public List<NotificationRecord> findByTrackedProductIdOrderBySentAtDesc(Long trackedProductId) {
        if (trackedProductId == null) return Collections.emptyList();

        List<NotificationRecord> list = new ArrayList<>();
        if (firestore == null) {
            for (NotificationRecord nr : inMemoryNotifications.values()) {
                if (trackedProductId.equals(nr.getTrackedProductId())) {
                    list.add(nr);
                }
            }
        } else {
            try {
                ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                        .whereEqualTo("trackedProductId", trackedProductId)
                        .get();
                for (DocumentSnapshot doc : future.get().getDocuments()) {
                    NotificationRecord nr = fromSnapshot(doc);
                    if (nr != null) {
                        list.add(nr);
                        inMemoryNotifications.put(nr.getId(), nr);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to query notifications for trackedProductId [{}] in Firestore: {}", trackedProductId, e.getMessage());
                for (NotificationRecord nr : inMemoryNotifications.values()) {
                    if (trackedProductId.equals(nr.getTrackedProductId())) {
                        list.add(nr);
                    }
                }
            }
        }

        list.sort((a, b) -> {
            LocalDateTime ta = a.getSentAt() != null ? a.getSentAt() : LocalDateTime.MIN;
            LocalDateTime tb = b.getSentAt() != null ? b.getSentAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });

        return list;
    }

    @Override
    public List<NotificationRecord> findTop20ByOrderBySentAtDesc() {
        List<NotificationRecord> all = findAll();
        all.sort((a, b) -> {
            LocalDateTime ta = a.getSentAt() != null ? a.getSentAt() : LocalDateTime.MIN;
            LocalDateTime tb = b.getSentAt() != null ? b.getSentAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });

        if (all.size() > 20) {
            return all.subList(0, 20);
        }
        return all;
    }

    @Override
    public long count() {
        if (firestore == null) {
            return inMemoryNotifications.size();
        }
        try {
            return firestore.collection(COLLECTION_NAME).get().get().size();
        } catch (Exception e) {
            return inMemoryNotifications.size();
        }
    }

    private Map<String, Object> toMap(NotificationRecord nr) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", nr.getId());
        map.put("trackedProductId", nr.getTrackedProductId());
        map.put("recipientEmail", nr.getRecipientEmail());
        map.put("previousPrice", nr.getPreviousPrice());
        map.put("newPrice", nr.getNewPrice());
        map.put("dropAmount", nr.getDropAmount());
        map.put("dropPercentage", nr.getDropPercentage());
        map.put("status", nr.getStatus());
        map.put("message", nr.getMessage());
        map.put("sentAt", nr.getSentAt() != null ? nr.getSentAt().toString() : LocalDateTime.now().toString());
        return map;
    }

    private NotificationRecord fromSnapshot(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        NotificationRecord nr = new NotificationRecord();
        Long id = doc.getLong("id");
        if (id == null) {
            try { id = Long.parseLong(doc.getId()); } catch (Exception ignored) {}
        }
        nr.setId(id);
        nr.setTrackedProductId(doc.getLong("trackedProductId"));
        nr.setRecipientEmail(doc.getString("recipientEmail"));
        nr.setPreviousPrice(doc.getDouble("previousPrice"));
        nr.setNewPrice(doc.getDouble("newPrice"));
        nr.setDropAmount(doc.getDouble("dropAmount"));
        nr.setDropPercentage(doc.getDouble("dropPercentage"));
        nr.setStatus(doc.getString("status"));
        nr.setMessage(doc.getString("message"));

        String sentAtStr = doc.getString("sentAt");
        if (sentAtStr != null) {
            try { nr.setSentAt(LocalDateTime.parse(sentAtStr)); } catch (Exception ignored) {}
        }
        return nr;
    }
}
