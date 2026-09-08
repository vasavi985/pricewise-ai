package com.pricewise.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class DistributedIdGeneratorTest {

    @Test
    void testBasicProperties_PositiveAndSafeInteger() {
        DistributedIdGenerator generator = new DistributedIdGenerator(1L);

        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0, "ID must be positive");
            assertTrue(id <= DistributedIdGenerator.MAX_SAFE_INTEGER, "ID must be <= JavaScript Number.MAX_SAFE_INTEGER");
        }
    }

    @Test
    void testMonotonicity_SuccessiveIdsIncrease() {
        DistributedIdGenerator generator = new DistributedIdGenerator(2L);

        long previous = generator.nextId();
        for (int i = 0; i < 1000; i++) {
            long current = generator.nextId();
            assertTrue(current > previous, "Successive IDs must strictly increase: " + current + " > " + previous);
            previous = current;
        }
    }

    @Test
    void testSequenceRollover_GeneratesMoreThan256InOneBurst() {
        DistributedIdGenerator generator = new DistributedIdGenerator(3L);

        Set<Long> generated = ConcurrentHashMap.newKeySet();
        int burstSize = 1000; // More than 256 sequence capacity

        for (int i = 0; i < burstSize; i++) {
            long id = generator.nextId();
            assertTrue(generated.add(id), "Duplicate ID encountered during burst: " + id);
        }

        assertEquals(burstSize, generated.size(), "All burst IDs must be unique");
    }

    @Test
    void testHighConcurrency_50ThreadsZeroDuplicates() throws InterruptedException {
        DistributedIdGenerator generator = new DistributedIdGenerator(5L);

        int threadCount = 50;
        int idsPerThread = 500;
        int totalExpected = threadCount * idsPerThread;

        Set<Long> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(generator.nextId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(totalExpected, ids.size(), "Encountered duplicate IDs during concurrent generation!");
    }

    @Test
    void testClockRollbackProtection_SmallDriftRecovers() {
        AtomicLong simulatedTime = new AtomicLong(System.currentTimeMillis());

        DistributedIdGenerator generator = new DistributedIdGenerator(4L) {
            @Override
            protected long timeGen() {
                return simulatedTime.get();
            }
        };

        long id1 = generator.nextId();

        // Simulate 2ms backwards drift
        simulatedTime.addAndGet(-2);

        // Within 10ms, it should recover when clock advances
        new Thread(() -> {
            try {
                Thread.sleep(1);
                simulatedTime.addAndGet(5);
            } catch (InterruptedException ignored) {}
        }).start();

        long id2 = generator.nextId();
        assertTrue(id2 > id1, "Recovered ID must be strictly greater than previous ID");
    }

    @Test
    void testClockRollbackProtection_LargeDriftThrowsException() {
        AtomicLong simulatedTime = new AtomicLong(System.currentTimeMillis());

        DistributedIdGenerator generator = new DistributedIdGenerator(4L) {
            @Override
            protected long timeGen() {
                return simulatedTime.get();
            }
        };

        generator.nextId();

        // Simulate 100ms backwards drift
        simulatedTime.addAndGet(-100);

        assertThrows(IllegalStateException.class, generator::nextId,
                "Must refuse ID generation when clock moves backwards significantly");
    }

    @Test
    void testNodeIdBoundaries() {
        assertDoesNotThrow(() -> new DistributedIdGenerator(0L));
        assertDoesNotThrow(() -> new DistributedIdGenerator(15L));
        assertThrows(IllegalArgumentException.class, () -> new DistributedIdGenerator(-1L));
        assertThrows(IllegalArgumentException.class, () -> new DistributedIdGenerator(16L));
    }
}
