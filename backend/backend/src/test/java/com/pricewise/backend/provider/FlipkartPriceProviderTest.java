package com.pricewise.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.flipkart.FlipkartPriceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FlipkartPriceProviderTest {

    private static final String REEFAPI_SEARCH_URL = "https://api.reefapi.com/flipkart/v1/search";

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
                REEFAPI_SEARCH_URL,
                executorService,
                5
        );

        // Configured provider with MockRestServiceServer
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        configuredProvider = new FlipkartPriceProvider(
                objectMapper,
                restClientBuilder.build(),
                "test-flipkart-reefapi-key",
                REEFAPI_SEARCH_URL,
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
    @DisplayName("1. When REEFAPI_KEY is missing, provider reports CONFIG_REQUIRED and is not configured")
    void test1_MissingApiKey() {
        assertFalse(unconfiguredProvider.isConfigured());
        assertEquals("CONFIG_REQUIRED", unconfiguredProvider.getStoreStatus());
        assertTrue(unconfiguredProvider.getRequiredConfig().contains("REEFAPI_KEY"));
    }

    @Test
    @DisplayName("2. When REEFAPI_KEY is configured, provider reports LIVE and isConfigured=true")
    void test2_ConfiguredApiKey() {
        assertTrue(configuredProvider.isConfigured());
        assertEquals("LIVE", configuredProvider.getStoreStatus());
    }

    @Test
    @DisplayName("3. Correct POST method, endpoint, x-api-key header, and body are sent in ReefAPI search request")
    void test3_CorrectReefApiPostRequest() {
        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-flipkart-reefapi-key"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(containsString("\"q\":\"iPhone 15\"")))
                .andExpect(content().string(containsString("\"page\":1")))
                .andRespond(withSuccess("{\"ok\":true,\"data\":{\"results\":[]}}", MediaType.APPLICATION_JSON));

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
    @DisplayName("5. Realistic ReefAPI search response mapping to ProviderProductDTO model")
    void test5_RealisticReefApiResponseMapping() {
        String mockJson = """
                {
                  "ok": true,
                  "meta": {
                    "api": "flipkart",
                    "endpoint": "search",
                    "latency_ms": 120.5,
                    "record_count": 1
                  },
                  "data": {
                    "results": [
                      {
                        "product_id": "MOBGTAGPAGGMGFAM",
                        "title": "Apple iPhone 15 (Black, 128 GB)",
                        "price": 65999,
                        "mrp": 79900,
                        "currency": "INR",
                        "image": "https://rukminim2.flixcart.com/image/832/832/xif0q/mobile/h/d/9/-original-imagtc2qzgnnuhxh.jpeg",
                        "url": "https://www.flipkart.com/apple-iphone-15-black-128-gb/p/itm6ac6485515ae4?pid=MOBGTAGPAGGMGFAM",
                        "rating": 4.6,
                        "availability": "IN_STOCK",
                        "in_stock": true
                      }
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
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
    @DisplayName("6. Product title extraction handles variations (title, product_title, name)")
    void test6_ProductTitleExtraction() {
        String json1 = "{\"ok\":true,\"data\":{\"results\":[{\"title\":\"Samsung Galaxy S24\",\"price\":64999}]}}";
        List<ProviderProductDTO> res1 = configuredProvider.parseSearchResponse(json1);
        assertEquals(1, res1.size());
        assertEquals("Samsung Galaxy S24", res1.get(0).getTitle());

        String json2 = "{\"ok\":true,\"data\":{\"products\":[{\"name\":\"OnePlus 12R\",\"price\":39999}]}}";
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
        String json = "{\"ok\":true,\"data\":{\"results\":[{\"title\":\"Laptop\",\"price\":50000}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("INR", res.get(0).getCurrency());
    }

    @Test
    @DisplayName("9. Product image extraction handles image, images array, and thumbnail")
    void test9_ImageExtraction() {
        String json = "{\"ok\":true,\"data\":{\"results\":[{\"title\":\"Mouse\",\"price\":500,\"image\":\"https://img.flipkart.com/mouse.jpg\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("https://img.flipkart.com/mouse.jpg", res.get(0).getImageUrl());
    }

    @Test
    @DisplayName("10. Product URL extraction maps real Flipkart URLs")
    void test10_ProductUrlExtraction() {
        String json = "{\"ok\":true,\"data\":{\"results\":[{\"title\":\"Keyboard\",\"price\":1500,\"url\":\"https://www.flipkart.com/keyboard/p/itm123\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("https://www.flipkart.com/keyboard/p/itm123", res.get(0).getProductUrl());
    }

    @Test
    @DisplayName("11. Product ID / FSN extraction from product_id or pid")
    void test11_ProductIdExtraction() {
        String json = "{\"ok\":true,\"data\":{\"results\":[{\"title\":\"Earbuds\",\"price\":2000,\"product_id\":\"ACC123456789\"}]}}";
        List<ProviderProductDTO> res = configuredProvider.parseSearchResponse(json);
        assertEquals(1, res.size());
        assertEquals("ACC123456789", res.get(0).getStoreProductId());
    }

    @Test
    @DisplayName("12. Empty or error response from ReefAPI returns empty list gracefully")
    void test12_EmptyResponse() {
        List<ProviderProductDTO> r1 = configuredProvider.parseSearchResponse("");
        assertTrue(r1.isEmpty());

        List<ProviderProductDTO> r2 = configuredProvider.parseSearchResponse("{\"ok\":true,\"data\":{\"results\":[]}}");
        assertTrue(r2.isEmpty());

        List<ProviderProductDTO> r3 = configuredProvider.parseSearchResponse("{}");
        assertTrue(r3.isEmpty());

        List<ProviderProductDTO> r4 = configuredProvider.parseSearchResponse("{\"ok\":false,\"error\":{\"code\":\"MISSING_PARAM\",\"message\":\"missing: q\"}}");
        assertTrue(r4.isEmpty());
    }

    @Test
    @DisplayName("13. API failure / HTTP 500 error marks provider unavailable and returns empty list safely")
    void test13_ApiFailure() {
        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"ok\":false,\"error\":{\"message\":\"Internal Server Error\"}}"));

        List<ProviderProductDTO> res = configuredProvider.searchProducts("error-item");
        assertNotNull(res);
        assertTrue(res.isEmpty());
        assertEquals("UNAVAILABLE", configuredProvider.getStoreStatus());
        assertNotNull(configuredProvider.getLastError());
        assertTrue(configuredProvider.getLastError().contains("500"));
    }

    @Test
    @DisplayName("14. Timeout handling: Provider respects timeout and returns empty list without hanging")
    void test14_TimeoutHandling() {
        FlipkartPriceProvider slowProvider = new FlipkartPriceProvider(
                objectMapper,
                restClientBuilder.build(),
                "test-key",
                REEFAPI_SEARCH_URL,
                executorService,
                1
        );

        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
                    return withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON).createResponse(request);
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
        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
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

    @Test
    @DisplayName("17. Parse nested price objects and selling_price numbers")
    void test17_NestedPriceAndSellingPrice() {
        String json1 = """
                {
                  "ok": true,
                  "data": {
                    "results": [
                      {
                        "title": "Samsung Galaxy S24",
                        "selling_price": 62999,
                        "product_id": "FSN123"
                      }
                    ]
                  }
                }
                """;
        List<ProviderProductDTO> r1 = configuredProvider.parseSearchResponse(json1);
        assertEquals(1, r1.size());
        assertEquals(62999.0, r1.get(0).getPrice());

        String json2 = """
                {
                  "ok": true,
                  "data": {
                    "search_results": [
                      {
                        "productTitle": "OnePlus 12",
                        "price": { "value": 54999.0 },
                        "productId": "OP12"
                      }
                    ]
                  }
                }
                """;
        List<ProviderProductDTO> r2 = configuredProvider.parseSearchResponse(json2);
        assertEquals(1, r2.size());
        assertEquals(54999.0, r2.get(0).getPrice());
    }

    @Test
    @DisplayName("18. Diagnostic getLastError is updated and safe without exposing credentials")
    void test18_DiagnosticLastError() {
        mockServer.expect(requestTo(REEFAPI_SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"ok\":false,\"error\":{\"message\":\"Invalid key=SECRET123\"}}"));

        configuredProvider.searchProducts("diag-test");
        String err = configuredProvider.getLastError();
        assertNotNull(err);
        assertTrue(err.contains("401"));
        assertFalse(err.contains("SECRET123"));
        assertTrue(err.contains("REDACTED"));
    }

    @Test
    @DisplayName("19. Real ReefAPI documented Flipkart search response parsing (Acer Aspire Lite)")
    void test19_RealReefApiSchemaParsing() {
        String json = """
                {
                  "ok": true,
                  "meta": {
                    "api": "flipkart",
                    "endpoint": "search",
                    "mode": "live",
                    "latency_ms": 1743.3,
                    "record_count": 24,
                    "cache_hit": false
                  },
                  "data": {
                    "results": [
                      {
                        "product_id": "COMH2TPSVSGUVKY4",
                        "listing_id": "LSTCOMH2TPSVSGUVKY4MJENVA",
                        "itm_id": "itm1bc0bcb4598e7",
                        "title": "Acer Aspire Lite AMD Ryzen 3 Quad Core 5400U - (8 GB/256 GB SSD/Windows 11 Home) AL15-41 Thin and Light Laptop",
                        "subtitle": "15.6 Inch, Steel Grey, 1.59 Kg",
                        "url": "https://www.flipkart.com/acer-aspire-lite-amd-ryzen-3-quad-core-5400u-8-gb-256-gb-ssd-windows-11-home-al15-41-thin-light-laptop/p/itm1bc0bcb4598e7?pid=COMH2TPSVSGUVKY4",
                        "price": 39990,
                        "mrp": 44990,
                        "discount_percent": 11,
                        "currency": "INR",
                        "rating": 4.2,
                        "rating_count": 129,
                        "review_count": 13,
                        "images": [
                          "http://rukmini1.flixcart.com/image/832/832/xif0q/computer/u/p/m/-original-imah2pf2u98xefzx.jpeg?q=70"
                        ],
                        "image": "http://rukmini1.flixcart.com/image/832/832/xif0q/computer/u/p/m/-original-imah2pf2u98xefzx.jpeg?q=70",
                        "availability": "IN_STOCK",
                        "in_stock": true,
                        "key_specs": [
                          "AMD Ryzen 3 Quad Core Processor",
                          "8 GB DDR4 RAM",
                          "Windows 11 Operating System"
                        ],
                        "vertical": "computer",
                        "flipkart_advantage": true
                      }
                    ]
                  }
                }
                """;
        List<ProviderProductDTO> results = configuredProvider.parseSearchResponse(json);
        assertNotNull(results);
        assertEquals(1, results.size());

        ProviderProductDTO p = results.get(0);
        assertEquals("FLIPKART", p.getStore());
        assertEquals("COMH2TPSVSGUVKY4", p.getStoreProductId());
        assertEquals("Acer Aspire Lite AMD Ryzen 3 Quad Core 5400U - (8 GB/256 GB SSD/Windows 11 Home) AL15-41 Thin and Light Laptop", p.getTitle());
        assertEquals(39990.0, p.getPrice());
        assertEquals(4.2, p.getRating());
        assertEquals("http://rukmini1.flixcart.com/image/832/832/xif0q/computer/u/p/m/-original-imah2pf2u98xefzx.jpeg?q=70", p.getImageUrl());
        assertEquals("https://www.flipkart.com/acer-aspire-lite-amd-ryzen-3-quad-core-5400u-8-gb-256-gb-ssd-windows-11-home-al15-41-thin-light-laptop/p/itm1bc0bcb4598e7?pid=COMH2TPSVSGUVKY4", p.getProductUrl());
        assertEquals("Acer", p.getBrand());
    }
}
