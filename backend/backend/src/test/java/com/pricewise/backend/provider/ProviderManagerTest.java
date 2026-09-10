package com.pricewise.backend.provider;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.dto.ProviderStatusDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ProviderManagerTest {

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private ProviderProductDTO createMockProduct(String store, String id, String title, Double price) {
        return new ProviderProductDTO(
                store, id, title, title, "Brand", "Model", "Category",
                "Description", "http://img.com/pic.jpg", "http://store.com/item",
                price, "INR", "IN_STOCK", "LIVE", 4.5
        );
    }

    private PriceProvider createStubProvider(String name, long delayMs, List<ProviderProductDTO> items, boolean throwException) {
        return new PriceProvider() {
            @Override
            public String getStoreName() {
                return name;
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String getStoreStatus() {
                return "LIVE";
            }

            @Override
            public String getRequiredConfig() {
                return "None";
            }

            @Override
            public String getDescription() {
                return name + " Provider";
            }

            @Override
            public List<ProviderProductDTO> searchProducts(String query) {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Collections.emptyList();
                    }
                }
                if (throwException) {
                    throw new RuntimeException("Simulated API failure for " + name);
                }
                return items != null ? items : Collections.emptyList();
            }

            @Override
            public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
                return null;
            }
        };
    }

    @Test
    @DisplayName("Providers execute concurrently rather than sequentially (2x 350ms finish in < 600ms)")
    void testConcurrentExecutionOfMultipleProviders() {
        ProviderProductDTO item1 = createMockProduct("STORE_A", "A1", "Product A", 100.0);
        ProviderProductDTO item2 = createMockProduct("STORE_B", "B1", "Product B", 200.0);

        PriceProvider providerA = createStubProvider("STORE_A", 350, List.of(item1), false);
        PriceProvider providerB = createStubProvider("STORE_B", 350, List.of(item2), false);

        ProviderManager manager = new ProviderManager(List.of(providerA, providerB), executorService, 5);

        long start = System.currentTimeMillis();
        List<ProviderProductDTO> results = manager.searchAll("test query");
        long elapsed = System.currentTimeMillis() - start;

        // If sequential, elapsed >= 700ms. Since concurrent, elapsed should be ~350-550ms.
        assertTrue(elapsed < 650, "Concurrent execution should complete well under sequential 700ms, elapsed was: " + elapsed + "ms");
        assertEquals(2, results.size());
        assertEquals("STORE_A", results.get(0).getStore());
        assertEquals("STORE_B", results.get(1).getStore());
    }

    @Test
    @DisplayName("A failing provider does not prevent other providers from returning results")
    void testFailingProviderDoesNotBlockSuccessfulProviders() {
        ProviderProductDTO itemSuccess = createMockProduct("STORE_GOOD", "G1", "Good Product", 999.0);

        PriceProvider failingProvider = createStubProvider("STORE_BAD", 50, null, true);
        PriceProvider successfulProvider = createStubProvider("STORE_GOOD", 50, List.of(itemSuccess), false);

        ProviderManager manager = new ProviderManager(List.of(failingProvider, successfulProvider), executorService, 5);

        List<ProviderProductDTO> results = manager.searchAll("test query");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("STORE_GOOD", results.get(0).getStore());
        assertEquals("Good Product", results.get(0).getTitle());
    }

    @Test
    @DisplayName("A slow provider hitting overall timeout does not block faster completed providers")
    void testSlowProviderExceedingTimeoutDoesNotBlockFastProviders() {
        ProviderProductDTO fastItem = createMockProduct("FAST_STORE", "F1", "Fast Product", 499.0);

        PriceProvider fastProvider = createStubProvider("FAST_STORE", 50, List.of(fastItem), false);
        PriceProvider slowProvider = createStubProvider("SLOW_STORE", 3000, Collections.emptyList(), false);

        // Set search timeout to 1 second
        ProviderManager manager = new ProviderManager(List.of(fastProvider, slowProvider), executorService, 1);

        long start = System.currentTimeMillis();
        List<ProviderProductDTO> results = manager.searchAll("headsets");
        long elapsed = System.currentTimeMillis() - start;

        // Should return around 1s, not waiting 3s for slow provider
        assertTrue(elapsed >= 900 && elapsed < 2000, "Should abort slow provider around 1s, elapsed was: " + elapsed + "ms");
        assertEquals(1, results.size());
        assertEquals("FAST_STORE", results.get(0).getStore());
    }

    @Test
    @DisplayName("Result ordering is deterministic based on provider registration order")
    void testDeterministicResultOrdering() {
        ProviderProductDTO itemA = createMockProduct("STORE_1", "1", "Item 1", 10.0);
        ProviderProductDTO itemB = createMockProduct("STORE_2", "2", "Item 2", 20.0);

        // Store 1 takes longer (200ms) than Store 2 (50ms)
        PriceProvider slowerFirst = createStubProvider("STORE_1", 200, List.of(itemA), false);
        PriceProvider fasterSecond = createStubProvider("STORE_2", 50, List.of(itemB), false);

        ProviderManager manager = new ProviderManager(List.of(slowerFirst, fasterSecond), executorService, 5);

        List<ProviderProductDTO> results = manager.searchAll("query");

        assertEquals(2, results.size());
        // Despite Store 2 finishing first, Store 1 results must come first due to registration order
        assertEquals("STORE_1", results.get(0).getStore());
        assertEquals("STORE_2", results.get(1).getStore());
    }

    @Test
    @DisplayName("Provider statuses and provider lookup by store name operate correctly for Amazon and Flipkart")
    void testProviderStatusesAndLookup() {
        PriceProvider providerA = createStubProvider("AMAZON", 0, null, false);
        PriceProvider providerB = createStubProvider("FLIPKART", 0, null, false);

        ProviderManager manager = new ProviderManager(List.of(providerA, providerB), executorService);

        List<ProviderStatusDTO> statuses = manager.getProviderStatuses();
        assertEquals(2, statuses.size());
        assertEquals("AMAZON", statuses.get(0).getStore());
        assertEquals("FLIPKART", statuses.get(1).getStore());

        assertNotNull(manager.getProvider("AMAZON"));
        assertNotNull(manager.getProvider("flipkart"));
        assertNull(manager.getProvider("UNKNOWN_STORE"));
    }

    @Test
    @DisplayName("Amazon and Flipkart execute concurrently through ProviderManager with failure isolation")
    void testAmazonAndFlipkartConcurrentExecution() {
        ProviderProductDTO amzItem = createMockProduct("AMAZON", "B01", "Apple iPhone 15", 69990.0);
        ProviderProductDTO flpItem = createMockProduct("FLIPKART", "FK01", "Apple iPhone 15", 65999.0);

        PriceProvider amazon = createStubProvider("AMAZON", 50, List.of(amzItem), false);
        PriceProvider flipkart = createStubProvider("FLIPKART", 50, List.of(flpItem), false);

        ProviderManager manager = new ProviderManager(List.of(amazon, flipkart), executorService, 5);

        List<ProviderProductDTO> results = manager.searchAll("iPhone 15");
        assertEquals(2, results.size());
        assertEquals("AMAZON", results.get(0).getStore());
        assertEquals("FLIPKART", results.get(1).getStore());

        // When Flipkart fails, Amazon still returns safely
        PriceProvider failingFlipkart = createStubProvider("FLIPKART", 0, null, true);
        ProviderManager failoverManager = new ProviderManager(List.of(amazon, failingFlipkart), executorService, 5);
        List<ProviderProductDTO> failoverResults = failoverManager.searchAll("iPhone 15");
        assertEquals(1, failoverResults.size());
        assertEquals("AMAZON", failoverResults.get(0).getStore());
    }
}
