package com.pricewise.backend.repository;

import com.pricewise.backend.entity.UserSearchHistory;
import com.pricewise.backend.repository.firestore.FirestoreUserSearchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirestoreUserSearchHistoryRepositoryTest {

    private FirestoreUserSearchHistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FirestoreUserSearchHistoryRepository(null);
    }

    @Test
    void testUserHistoryIsolation() {
        String userA = "user-A-uid";
        String userB = "user-B-uid";

        UserSearchHistory h1 = new UserSearchHistory("h1", userA, "iPhone 15");
        h1.setResultCount(5);
        h1.setTopProductName("iPhone 15 128GB");
        h1.setCreatedAt(LocalDateTime.now().minusHours(2));
        repository.save(h1);

        UserSearchHistory h2 = new UserSearchHistory("h2", userA, "MacBook Pro M3");
        h2.setResultCount(3);
        h2.setTopProductName("MacBook Pro 14");
        h2.setCreatedAt(LocalDateTime.now().minusHours(1));
        repository.save(h2);

        UserSearchHistory h3 = new UserSearchHistory("h3", userB, "Sony WH-1000XM5");
        h3.setResultCount(4);
        h3.setTopProductName("Sony Noise Canceling Headphones");
        h3.setCreatedAt(LocalDateTime.now());
        repository.save(h3);

        // User A must ONLY see User A's history
        List<UserSearchHistory> historyA = repository.findByUserId(userA);
        assertEquals(2, historyA.size());
        assertEquals("MacBook Pro M3", historyA.get(0).getQuery()); // Most recent first
        assertEquals("iPhone 15", historyA.get(1).getQuery());

        // User B must ONLY see User B's history
        List<UserSearchHistory> historyB = repository.findByUserId(userB);
        assertEquals(1, historyB.size());
        assertEquals("Sony WH-1000XM5", historyB.get(0).getQuery());

        // Clearing User A's history does NOT affect User B
        repository.deleteAllByUserId(userA);
        assertTrue(repository.findByUserId(userA).isEmpty());
        assertEquals(1, repository.findByUserId(userB).size());
    }
}
