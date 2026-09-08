package com.pricewise.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.live.LiveCommercePriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveCommercePriceProviderTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Provider metadata reports OPEN_COMMERCE, LIVE status, and configured")
    void testProviderMetadata() {
        LiveCommercePriceProvider provider = new LiveCommercePriceProvider(objectMapper);
        assertEquals("OPEN_COMMERCE", provider.getStoreName());
        assertEquals("LIVE", provider.getStoreStatus());
        assertTrue(provider.isConfigured());
        assertNotNull(provider.getDescription());
        assertNotNull(provider.getRequiredConfig());
    }

    @Test
    @DisplayName("searchProducts parses valid dummyjson.com search response successfully")
    void testSearchProductsSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockClient = builder.baseUrl("https://dummyjson.com").build();

        String responseJson = """
                {
                    "products": [
                        {
                            "id": 1,
                            "title": "iPhone 9",
                            "description": "An apple mobile which is nothing like apple",
                            "price": 549,
                            "discountPercentage": 12.96,
                            "rating": 4.69,
                            "stock": 94,
                            "brand": "Apple",
                            "category": "smartphones",
                            "thumbnail": "https://dummyjson.com/image/i/products/1/thumbnail.jpg"
                        }
                    ],
                    "total": 1,
                    "skip": 0,
                    "limit": 6
                }
                """;

        server.expect(requestTo("https://dummyjson.com/products/search?q=iPhone&limit=6"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        LiveCommercePriceProvider provider = new LiveCommercePriceProvider(
                objectMapper,
                mockClient,
                executor,
                5
        );

        List<ProviderProductDTO> results = provider.searchProducts("iPhone");
        assertNotNull(results);
        assertEquals(1, results.size());
        ProviderProductDTO p = results.get(0);
        assertEquals("OPEN_COMMERCE", p.getStore());
        assertEquals("LIVE-1", p.getStoreProductId());
        assertEquals("iPhone 9", p.getTitle());
        assertEquals("Apple", p.getBrand());
        assertEquals("smartphones", p.getCategory());
        assertEquals(Math.round(549 * 86.0), p.getPrice());
        assertEquals("LIVE", p.getStatus());
        assertEquals("IN_STOCK", p.getAvailability());
        assertEquals(4.69, p.getRating());

        server.verify();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("searchProducts aborts and returns empty list when execution exceeds hard timeout")
    void testSearchProductsTimeout() {
        ExecutorService slowExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                r.run();
            });
            t.setDaemon(true);
            return t;
        });

        LiveCommercePriceProvider timeoutProvider = new LiveCommercePriceProvider(
                objectMapper,
                RestClient.builder().build(),
                slowExecutor,
                1 // 1-second timeout for fast test execution
        );

        long start = System.currentTimeMillis();
        List<ProviderProductDTO> results = timeoutProvider.searchProducts("timeout test");
        long duration = System.currentTimeMillis() - start;

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Timed out search should return empty list without throwing");
        assertTrue(duration >= 900 && duration < 2500, "Should abort around 1s, duration was: " + duration);

        slowExecutor.shutdownNow();
    }

    @Test
    @DisplayName("searchProducts handles server error safely and returns empty list")
    void testSearchProductsFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockClient = builder.baseUrl("https://dummyjson.com").build();

        server.expect(requestTo("https://dummyjson.com/products/search?q=error&limit=6"))
                .andRespond(withServerError());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        LiveCommercePriceProvider provider = new LiveCommercePriceProvider(
                objectMapper,
                mockClient,
                executor,
                5
        );

        List<ProviderProductDTO> results = provider.searchProducts("error");
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Failed search should return empty list without throwing exception");

        server.verify();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("fetchCurrentPrice parses single product response and respects timeout")
    void testFetchCurrentPriceSuccessAndTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockClient = builder.baseUrl("https://dummyjson.com").build();

        String singleProductJson = """
                {
                    "id": 1,
                    "title": "iPhone 9",
                    "description": "An apple mobile which is nothing like apple",
                    "price": 549,
                    "rating": 4.69,
                    "brand": "Apple",
                    "category": "smartphones",
                    "thumbnail": "https://dummyjson.com/image/i/products/1/thumbnail.jpg"
                }
                """;

        server.expect(requestTo("https://dummyjson.com/products/1"))
                .andRespond(withSuccess(singleProductJson, MediaType.APPLICATION_JSON));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        LiveCommercePriceProvider provider = new LiveCommercePriceProvider(
                objectMapper,
                mockClient,
                executor,
                5
        );

        ProviderProductDTO item = provider.fetchCurrentPrice("LIVE-1", null);
        assertNotNull(item);
        assertEquals("LIVE-1", item.getStoreProductId());
        assertEquals("iPhone 9", item.getTitle());
        assertEquals(Math.round(549 * 86.0), item.getPrice());
        server.verify();
        executor.shutdownNow();

        // Test timeout on fetchCurrentPrice
        ExecutorService slowExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
                r.run();
            });
            t.setDaemon(true);
            return t;
        });

        LiveCommercePriceProvider timeoutProvider = new LiveCommercePriceProvider(
                objectMapper,
                RestClient.builder().build(),
                slowExecutor,
                1
        );

        ProviderProductDTO timeoutResult = timeoutProvider.fetchCurrentPrice("LIVE-1", null);
        assertNull(timeoutResult, "Timed out fetchCurrentPrice should return null");
        slowExecutor.shutdownNow();
    }
}
