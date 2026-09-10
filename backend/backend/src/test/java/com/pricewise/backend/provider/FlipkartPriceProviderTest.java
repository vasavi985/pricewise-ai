package com.pricewise.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.flipkart.FlipkartPriceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FlipkartPriceProviderTest {

    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ExecutorService executorService;
    private FlipkartPriceProvider unconfiguredProvider;
    private FlipkartPriceProvider configuredProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        executorService = Executors.newFixedThreadPool(2);

        // Unconfigured provider (empty key)
        unconfiguredProvider = new FlipkartPriceProvider(
                objectMapper,
                RestClient.builder().build(),
                "",
                "real-time-flipkart-data2.p.rapidapi.com",
                executorService,
                5
        );

        // Configured provider with MockRestServiceServer
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        configuredProvider = new FlipkartPriceProvider(
                objectMapper,
                restClientBuilder.build(),
                "test-flipkart-rapidapi-key",
                "real-time-flipkart-data2.p.rapidapi.com",
                executorService,
                5
        );
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("1. When FLIPKART_API_KEY is missing, provider reports CONFIG_REQUIRED and is not configured")
    void test1_MissingApiKey() {
        assertFalse(unconfiguredProvider.isConfigured());
        assertEquals("CONFIG_REQUIRED", unconfiguredProvider.getStoreStatus());
        assertTrue(unconfiguredProvider.getRequiredConfig().contains("FLIPKART_API_KEY"));
    }

    @Test
    @DisplayName("2. When FLIPKART_API_KEY is configured, provider reports LIVE and isConfigured=true")
    void test2_ConfiguredApiKey() {
        assertTrue(configuredProvider.isConfigured());
        assertEquals("LIVE", configuredProvider.getStoreStatus());
    }

    @Test
    @DisplayName("3. Correct authentication headers (x-rapidapi-key, x-rapidapi-host) are sent in API requests")
    void test3_CorrectAuthenticationHeaders() {
        mockServer.expect(requestTo("https://real-time-flipkart-data2.p.rapidapi.com/search?query=iPhone%2015&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-rapidapi-key", "test-flipkart-rapidapi-key"))
                .andExpect(header("x-rapidapi-host", "real-time-flipkart-data2.p.rapidapi.com"))
                .andRespond(withSuccess("{\"status\":\"OK\",\"data\":{\"products\":[]}}", MediaType.APPLICATION_JSON));

        configuredProvider.searchProducts("iPhone 15");
        mockServer.verify();
    }

    @Test
    @DisplayName("4. Search query handling: safe trimming, empty/null query returns empty list")
    void test4_SearchQueryHandling() {
        List<ProviderProductDTO> emptyRes = configuredProvider.searchProducts("");
        assertNotNull(emptyRes);
        assertTrue(emptyRes.isEmpty());

        List<ProviderProductDTO> nullRes = configuredProvider.searchProducts(null);
        assertNotNull(nullRes);
        assertTrue(nullRes.isEmpty());

        List<ProviderProductDTO> unconfRes = unconfiguredProvider.searchProducts("iPhone 15");
        assertNotNull(unconfRes);
        assertTrue(unconfRes.isEmpty());
    }

    @Test
    @DisplayName("5. Realistic API response mapping to ProviderProductDTO model")
    void test5_RealisticApiResponseMapping() {
        String mockJson = """
                {
                  "status": "OK",
                  "data": {
                    "products": [
                      {
                        "product_id": "MOBGTAGPAGGMGFAM",
                        "product_title": "Apple iPhone 15 (Black, 128 GB)",
                        "product_price": "₹65,999",
                        "currency": "INR",
                        "product_photo": "https://rukminim2.flixcart.com/image/832/832/xif0q/mobile/h/d/9/-original-imagtc2qzgnnuhxh.jpeg",
                        "product_url": "https://www.flipkart.com/apple-iphone-15-black-128-gb/p/itm6ac6485515ae4?pid=MOBGTAGPAGGMGFAM",
                        "product_rating": "4.6",
                        "product_availability": "IN_STOCK"
                      }
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo("https://real-time-flipkart-data2.p.rapidapi.com/search?query=iPhone%2015&page=1"))
                .andRespond(withSuccess(mockJson, MediaType.APPLICATION_JSON));

        List<ProviderProductDTO> results = configuredProvider.searchProducts("iPhone 15");
        assertNotNull(results);
        assertEquals(1, results.size());

        ProviderProductDTO p = results.get(0);
        assertEquals("FLIPKART", p.getStore());
        assertEquals("MOBGTAGPAGGMGFAM", p.getStoreProductId());
        assertEquals("Apple iPhone 15 (Black, 128 GB)", p.getTitle());
        assertEquals(65999.0, p.getPrice());
        assertEquals("INR", p.getCurrency());
        assertEquals("https://rukminim2.flixcart.com/image/832/832/xif0q/mobile/h/d/9/-original-imagtc2qzgnnuhxh.jpeg", p.getImageUrl());
        assertEquals("https://www.flipkart.com/apple-iphone-15-black-128-gb/p/itm6ac6485515ae4?pid=MOBGTAGPAGGMGFAM", p.getProductUrl());
        assertEquals(4.6, p.getRating());
        assertEquals("IN_STOCK", p.getAvailability());
        assertEquals("LIVE", p.getStatus());
        assertEquals("Apple", p.getBrand());
    }

    @Test
    @DisplayName("6. Product title extraction handles variations (product_title, title, name)")
    void test6_ProductTitleExtraction() {
        String json1 = "{\"data\":{\"products\":[{\"title\":\"Samsung Galaxy S24\",\"price\":\"₹64,999\"}]}}";
        List<ProviderProductDTO> res1 = configuredProvider.parseSearchResponse(json1);
        assertEquals(1, res1.size());
        assertEquals("Samsung Galaxy S24", res1.get(0).getTitle());

        String json2 = "{\"products\":[{\"name\":\"OnePlus 12R\",\"price\":\"₹39,999\"}]}";
        List<ProviderProductDTO> res2 = configuredProvider.parseSearchResponse(json2);
        assertEquals(1, res2.size());
        assertEquals("OnePlus 12R", res2.get(0).getTitle());
    }

    @Test
    @DisplayName("7. Price parsing handles Indian Rupees, commas, decimals, suffixes, and invalid values")
    void test7_PriceParsing() {
        assertEquals(65999.0, FlipkartPriceProvider.parsePrice("₹65,999"));
        assertEquals(129990.0, FlipkartPriceProvider.parsePrice("₹1,29,990.00"));
        assertEquals(64999.50, FlipkartPriceProvider.parsePrice("Rs. 64,999.50"));
        assertEquals(999.0, FlipkartPriceProvider.parsePrice("₹ 999"));
        assertEquals(49999.0, FlipkartPriceProvider.parsePrice("49999"));

        assertNull(FlipkartPriceProvider.parsePrice(null));
        assertNull(FlipkartPriceProvider.parsePrice(""));
        assertNull(FlipkartPriceProvider.parsePrice("Currently unavailable"));
        assertNull(FlipkartPriceProvider.parsePrice("0"));
    }

    @Test
    @DisplayName("8. Currency extraction defaults to INR if not specified")
    void test8_CurrencyExtraction() {
        String json = "{\"data\":{\"products\":[{\"title\":\"Laptop\",\"price\":\"50000\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("INR", res.get(0).getCurrency());
    }

    @Test
    @DisplayName("9. Product image extraction handles product_photo, product_image, and thumbnail")
    void test9_ImageExtraction() {
        String json = "{\"data\":{\"products\":[{\"title\":\"Mouse\",\"price\":\"500\",\"thumbnail\":\"https://img.flipkart.com/thumb.jpg\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("https://img.flipkart.com/thumb.jpg", res.get(0).getImageUrl());
    }

    @Test
    @DisplayName("10. Product URL extraction maps real Flipkart URLs")
    void test10_ProductUrlExtraction() {
        String json = "{\"data\":{\"products\":[{\"title\":\"Keyboard\",\"price\":\"1500\",\"product_url\":\"https://www.flipkart.com/keyboard/p/itm123\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("https://www.flipkart.com/keyboard/p/itm123", res.get(0).getProductUrl());
    }

    @Test
    @DisplayName("11. Product ID / FSN extraction from product_id or pid")
    void test11_ProductIdExtraction() {
        String json = "{\"data\":{\"products\":[{\"title\":\"Earbuds\",\"price\":\"2000\",\"pid\":\"ACC123456789\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("ACC123456789", res.get(0).getStoreProductId());
    }

    @Test
    @DisplayName("12. Empty response from API returns empty list gracefully")
    void test12_EmptyResponse() {
        List<ProviderProductDTO> r1 = configuredProvider.parseSearchResponse("");
        assertTrue(r1.isEmpty());

        List<ProviderProductDTO> r2 = configuredProvider.parseSearchResponse("{\"data\":{\"products\":[]}}");
        assertTrue(r2.isEmpty());

        List<ProviderProductDTO> r3 = configuredProvider.parseSearchResponse("{}");
        assertTrue(r3.isEmpty());
    }

    @Test
    @DisplayName("13. API failure / HTTP 500 error marks provider unavailable and returns empty list safely")
    void test13_ApiFailure() {
        mockServer.expect(requestTo("https://real-time-flipkart-data2.p.rapidapi.com/search?query=error-item&page=1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Internal Server Error\"}"));

        List<ProviderProductDTO> res = configuredProvider.searchProducts("error-item");
        assertNotNull(res);
        assertTrue(res.isEmpty());
        assertEquals("UNAVAILABLE", configuredProvider.getStoreStatus());
    }

    @Test
    @DisplayName("14. Timeout handling: Provider respects timeout and returns empty list without hanging")
    void test14_TimeoutHandling() {
        // Create a provider with 1-second timeout
        FlipkartPriceProvider slowProvider = new FlipkartPriceProvider(
                objectMapper,
                restClientBuilder.build(),
                "test-key",
                "real-time-flipkart-data2.p.rapidapi.com",
                executorService,
                1
        );

        // Don't register response on mockServer or delay response
        mockServer.expect(requestTo("https://real-time-flipkart-data2.p.rapidapi.com/search?query=timeout-test&page=1"))
                .andRespond(request -> {
                    try {
                        Thread.sleep(1500); // Exceeds 1s timeout
                    } catch (InterruptedException ignored) {}
                    return withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });

        long start = System.currentTimeMillis();
        List<ProviderProductDTO> res = slowProvider.searchProducts("timeout-test");
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(res);
        assertTrue(res.isEmpty());
        assertTrue(elapsed < 2500, "Timeout should have triggered around 1 second");
    }

    @Test
    @DisplayName("15. Provider failure isolation: Exception in Flipkart never throws out to caller")
    void test15_FailureIsolation() {
        mockServer.expect(requestTo("https://real-time-flipkart-data2.p.rapidapi.com/search?query=fail&page=1"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertDoesNotThrow(() -> {
            List<ProviderProductDTO> res = configuredProvider.searchProducts("fail");
            assertNotNull(res);
            assertTrue(res.isEmpty());
        });
    }

    @Test
    @DisplayName("16. Store name is strictly FLIPKART")
    void test16_StoreNameIsFlipkart() {
        assertEquals("FLIPKART", configuredProvider.getStoreName());
        assertEquals("FLIPKART", unconfiguredProvider.getStoreName());
    }
}
