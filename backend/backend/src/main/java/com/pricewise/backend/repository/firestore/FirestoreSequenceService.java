package com.pricewise.backend.repository.firestore;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FirestoreSequenceService {

    private final Firestore firestore;
    private final ConcurrentHashMap<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    public FirestoreSequenceService(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    public synchronized long getNextSequence(String sequenceName) {
        if (firestore == null) {
            return localCounters.computeIfAbsent(sequenceName, k -> new AtomicLong(0)).incrementAndGet();
        }

        try {
            DocumentReference docRef = firestore.collection("_counters").document("sequences");
            return firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                long current = 0L;
                if (snapshot.exists() && snapshot.contains(sequenceName)) {
                    Long val = snapshot.getLong(sequenceName);
                    if (val != null) current = val;
                }
                long next = current + 1;
                transaction.set(docRef, Collections.singletonMap(sequenceName, next), SetOptions.merge());
                return next;
            }).get();
        } catch (Exception e) {
            return localCounters.computeIfAbsent(sequenceName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public synchronized void ensureAtLeast(String sequenceName, long minVal) {
        localCounters.compute(sequenceName, (k, v) -> {
            if (v == null) return new AtomicLong(minVal);
            if (v.get() < minVal) v.set(minVal);
            return v;
        });

        if (firestore == null) {
            return;
        }

        try {
            DocumentReference docRef = firestore.collection("_counters").document("sequences");
            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                long current = 0L;
                if (snapshot.exists() && snapshot.contains(sequenceName)) {
                    Long val = snapshot.getLong(sequenceName);
                    if (val != null) current = val;
                }
                if (current < minVal) {
                    transaction.set(docRef, Collections.singletonMap(sequenceName, minVal), SetOptions.merge());
                }
                return null;
            }).get();
        } catch (Exception ignored) {
        }
    }
}
