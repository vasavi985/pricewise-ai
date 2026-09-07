package com.pricewise.backend.repository.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pricewise.backend.entity.UserSearchHistory;
import com.pricewise.backend.repository.UserSearchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class FirestoreUserSearchHistoryRepository implements UserSearchHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreUserSearchHistoryRepository.class);
    private static final String USERS_COLLECTION = "users";
    private static final String HISTORY_COLLECTION = "history";

    private final Firestore firestore;
    private final Map<String, List<UserSearchHistory>> inMemoryByUser = new ConcurrentHashMap<>();

    public FirestoreUserSearchHistoryRepository(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public UserSearchHistory save(UserSearchHistory history) {
        if (history == null || history.getUserId() == null) {
            throw new IllegalArgumentException("UserSearchHistory and userId must not be null.");
        }

        if (history.getId() == null || history.getId().trim().isEmpty()) {
            history.setId(UUID.randomUUID().toString());
        }
        if (history.getCreatedAt() == null) {
            history.setCreatedAt(LocalDateTime.now());
        }

        if (firestore == null) {
            inMemoryByUser.computeIfAbsent(history.getUserId(), k -> new CopyOnWriteArrayList<>()).add(0, history);
            return history;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", history.getId());
            data.put("userId", history.getUserId());
            data.put("query", history.getQuery());
            data.put("resultCount", history.getResultCount());
            data.put("topProductName", history.getTopProductName());
            data.put("topProductPrice", history.getTopProductPrice());
            data.put("topProductStore", history.getTopProductStore());
            data.put("topProductImage", history.getTopProductImage());
            data.put("topProductId", history.getTopProductId());
            data.put("createdAt", history.getCreatedAt().toString());

            firestore.collection(USERS_COLLECTION)
                    .document(history.getUserId())
                    .collection(HISTORY_COLLECTION)
                    .document(history.getId())
                    .set(data)
                    .get();

            return history;
        } catch (Exception e) {
            log.error("Failed to persist user search history to Firestore: {}", e.getMessage());
            inMemoryByUser.computeIfAbsent(history.getUserId(), k -> new CopyOnWriteArrayList<>()).add(0, history);
            return history;
        }
    }

    @Override
    public List<UserSearchHistory> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (firestore == null) {
            List<UserSearchHistory> list = inMemoryByUser.getOrDefault(userId, Collections.emptyList());
            List<UserSearchHistory> copy = new ArrayList<>(list);
            copy.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            return copy;
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(HISTORY_COLLECTION)
                    .get();

            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<UserSearchHistory> list = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                UserSearchHistory h = fromSnapshot(doc);
                if (h != null) {
                    list.add(h);
                }
            }

            list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            return list;
        } catch (Exception e) {
            log.error("Failed to query user search history from Firestore: {}", e.getMessage());
            List<UserSearchHistory> list = inMemoryByUser.getOrDefault(userId, Collections.emptyList());
            List<UserSearchHistory> copy = new ArrayList<>(list);
            copy.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            return copy;
        }
    }

    @Override
    public void deleteAllByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        inMemoryByUser.remove(userId);

        if (firestore == null) {
            return;
        }

        try {
            CollectionReference colRef = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(HISTORY_COLLECTION);

            ApiFuture<QuerySnapshot> future = colRef.get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : docs) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
            log.info("Successfully cleared {} history records for user [{}]", docs.size(), userId);
        } catch (Exception e) {
            log.error("Failed to delete user search history from Firestore: {}", e.getMessage());
        }
    }

    private UserSearchHistory fromSnapshot(DocumentSnapshot doc) {
        if (!doc.exists()) return null;

        UserSearchHistory h = new UserSearchHistory();
        h.setId(doc.getString("id"));
        h.setUserId(doc.getString("userId"));
        h.setQuery(doc.getString("query"));

        Long resCount = doc.getLong("resultCount");
        if (resCount != null) h.setResultCount(resCount.intValue());

        h.setTopProductName(doc.getString("topProductName"));
        h.setTopProductPrice(doc.getDouble("topProductPrice"));
        h.setTopProductStore(doc.getString("topProductStore"));
        h.setTopProductImage(doc.getString("topProductImage"));
        h.setTopProductId(doc.getLong("topProductId"));

        String createdStr = doc.getString("createdAt");
        if (createdStr != null) {
            try {
                h.setCreatedAt(LocalDateTime.parse(createdStr));
            } catch (Exception ignored) {
                h.setCreatedAt(LocalDateTime.now());
            }
        }

        return h;
    }
}
