package com.pricewise.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.security.SecureRandom;

/**
 * Distributed 53-bit Snowflake-style Long ID Generator.
 *
 * Bit Layout (53 bits total, strictly <= JavaScript Number.MAX_SAFE_INTEGER = 9,007,199,254,740,991):
 * - Bit 52-12 (41 bits): Milliseconds elapsed since custom epoch (2024-01-01T00:00:00Z). Covers ~69.7 years.
 * - Bit 11-8  (4 bits) : Node/Worker ID (0 to 15).
 * - Bit 7-0   (8 bits) : Sequence counter per millisecond (0 to 255, up to 256,000 IDs/sec per node).
 *
 * Guarantees:
 * 1. Positive 64-bit Long values in Java.
 * 2. Precision-safe in browser JavaScript (no rounding when JSON-parsed).
 * 3. Monotonically increasing across time within a generator instance.
 * 4. Thread-safe in-memory generation with zero database transactions.
 * 5. Disjoint from legacy IDs (1, 2, 3...) which are orders of magnitude smaller.
 */
@Component
public class DistributedIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(DistributedIdGenerator.class);

    // Custom epoch: 2024-01-01T00:00:00Z
    public static final long CUSTOM_EPOCH = 1704067200000L;

    public static final long NODE_ID_BITS = 4L;
    public static final long SEQUENCE_BITS = 8L;

    public static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1L; // 15
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L; // 255

    public static final long NODE_ID_SHIFT = SEQUENCE_BITS; // 8
    public static final long TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS; // 12

    public static final long MAX_SAFE_INTEGER = 9007199254740991L; // 2^53 - 1

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public DistributedIdGenerator() {
        this(resolveNodeId(null));
    }

    @Autowired
    public DistributedIdGenerator(@Value("${app.node-id:#{null}}") String configuredNodeId) {
        this(resolveNodeId(configuredNodeId));
    }

    public DistributedIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format("Node ID must be between 0 and %d (inclusive), given: %d", MAX_NODE_ID, nodeId));
        }
        this.nodeId = nodeId;
        log.info("DistributedIdGenerator initialized with Node ID: {} (Epoch: 2024-01-01T00:00:00Z)", this.nodeId);
    }

    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 10) {
                try {
                    Thread.sleep(offset + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentTimestamp = timeGen();
                if (currentTimestamp < lastTimestamp) {
                    throw new IllegalStateException(String.format("Clock moved backwards. Refusing to generate ID for %d ms", lastTimestamp - currentTimestamp));
                }
            } else {
                throw new IllegalStateException(String.format("Clock moved backwards by %d ms. Refusing to generate ID.", offset));
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 256 IDs generated in current millisecond, wait for next millisecond
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        long id = ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;

        if (id <= 0 || id > MAX_SAFE_INTEGER) {
            throw new IllegalStateException(String.format("Generated ID %d is out of safe range (0 < ID <= %d)", id, MAX_SAFE_INTEGER));
        }

        return id;
    }

    public long getNodeId() {
        return nodeId;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private static long resolveNodeId(String configuredNodeId) {
        if (configuredNodeId != null && !configuredNodeId.trim().isEmpty()) {
            try {
                long parsed = Long.parseLong(configuredNodeId.trim());
                if (parsed >= 0 && parsed <= MAX_NODE_ID) {
                    return parsed;
                }
                return Math.abs(parsed) % (MAX_NODE_ID + 1);
            } catch (NumberFormatException ignored) {
            }
        }

        String envNodeId = System.getenv("NODE_ID");
        if (envNodeId != null && !envNodeId.trim().isEmpty()) {
            try {
                long parsed = Long.parseLong(envNodeId.trim());
                if (parsed >= 0 && parsed <= MAX_NODE_ID) {
                    return parsed;
                }
                return Math.abs(parsed) % (MAX_NODE_ID + 1);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            String host = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return Math.abs((host + ":" + pid).hashCode()) % (MAX_NODE_ID + 1);
        } catch (Exception e) {
            return new SecureRandom().nextInt((int) (MAX_NODE_ID + 1));
        }
    }
}
