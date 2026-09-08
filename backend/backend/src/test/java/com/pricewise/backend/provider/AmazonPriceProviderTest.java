package com.pricewise.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.amazon.AmazonPriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmazonPriceProviderTest {

    private ObjectMapper objectMapper;
    private AmazonPriceProvider unconfiguredProvider;
    private AmazonPriceProvider configuredProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        unconfiguredProvider = new AmazonPriceProvider(objectMapper, RestClient.builder().build(), "", "real-time-amazon-data.p.rapidapi.com", "IN");
        configuredProvider = new AmazonPriceProvider(objectMapper, RestClient.builder().build(), "dummy-test-key", "real-time-amazon-data.p.rapidapi.com", "IN");
    }

    @Test
    @DisplayName("When RAPIDAPI_KEY is absent, provider reports CONFIG_REQUIRED and is not configured")
    void testUnconfiguredStatus() {
        assertFalse(unconfiguredProvider.isConfigured());
        assertEquals("CONFIG_REQUIRED", unconfiguredProvider.getStoreStatus());
        assertEquals("AMAZON", unconfiguredProvider.getStoreName());
        assertTrue(unconfiguredProvider.getRequiredConfig().contains("RAPIDAPI_KEY"));
    }

    @Test
    @DisplayName("When RAPIDAPI_KEY is set, provider reports LIVE")
    void testConfiguredStatus() {
        assertTrue(configuredProvider.isConfigured());
        assertEquals("LIVE", configuredProvider.getStoreStatus());
    }

    @Test
    @DisplayName("searchProducts returns empty list safely when unconfigured or query is empty")
    void testSearchProductsWhenUnconfiguredOrEmpty() {
        List<ProviderProductDTO> result = unconfiguredProvider.searchProducts("iphone 15");
        assertNotNull(result);
        assertTrue(result.isEmpty());

        List<ProviderProductDTO> emptyQuery = configuredProvider.searchProducts("");
        assertNotNull(emptyQuery);
        assertTrue(emptyQuery.isEmpty());

        List<ProviderProductDTO> nullQuery = configuredProvider.searchProducts(null);
        assertNotNull(nullQuery);
        assertTrue(nullQuery.isEmpty());
    }

    @Test
    @DisplayName("Price parsing handles Indian Rupee, USD, commas, decimals, and edge cases")
    void testParsePrice() {
        assertEquals(58999.0, AmazonPriceProvider.parsePrice("₹58,999"));
        assertEquals(129990.0, AmazonPriceProvider.parsePrice("₹1,29,990.00"));
        assertEquals(999.0, AmazonPriceProvider.parsePrice("₹ 999"));
        assertEquals(24999.50, AmazonPriceProvider.parsePrice("Rs. 24,999.50"));
        assertEquals(1099.99, AmazonPriceProvider.parsePrice("$1,099.99"));
        assertEquals(19.95, AmazonPriceProvider.parsePrice("$19.95"));
        assertEquals(59999.0, AmazonPriceProvider.parsePrice("59999"));
        assertEquals(149.50, AmazonPriceProvider.parsePrice("149.5"));

        assertNull(AmazonPriceProvider.parsePrice(null));
        assertNull(AmazonPriceProvider.parsePrice(""));
        assertNull(AmazonPriceProvider.parsePrice("   "));
        assertNull(AmazonPriceProvider.parsePrice("Currently unavailable"));
        assertNull(AmazonPriceProvider.parsePrice("0.00"));
    }

    @Test
    @DisplayName("extractAsin correctly resolves 10-char ASINs from IDs and real Amazon URLs")
    void testExtractAsin() {
        assertEquals("B0BSHF7WHW", AmazonPriceProvider.extractAsin("B0BSHF7WHW", null));
        assertEquals("B0CHX2F5QT", AmazonPriceProvider.extractAsin(null, "https://www.amazon.in/Apple-iPhone-15-128-GB/dp/B0CHX2F5QT/ref=sr_1_1"));
        assertEquals("B0BSHF7WHW", AmazonPriceProvider.extractAsin(null, "https://www.amazon.com/gp/product/B0BSHF7WHW"));
        assertEquals("B0D8X2T9PQ", AmazonPriceProvider.extractAsin(null, "https://www.amazon.in/dp/B0D8X2T9PQ"));

        assertNull(AmazonPriceProvider.extractAsin(null, null));
        assertNull(AmazonPriceProvider.extractAsin("INVALID", "https://google.com"));
        assertNull(AmazonPriceProvider.extractAsin("", ""));
    }

    @Test
    @DisplayName("parseRating extracts decimal rating from star rating text")
    void testParseRating() {
        assertEquals(4.5, AmazonPriceProvider.parseRating("4.5 out of 5 stars"));
        assertEquals(4.2, AmazonPriceProvider.parseRating("4.2"));
        assertEquals(5.0, AmazonPriceProvider.parseRating("5"));
        assertNull(AmazonPriceProvider.parseRating(null));
        assertNull(AmazonPriceProvider.parseRating("No reviews yet"));
    }

    @Test
    @DisplayName("extractBrand recognizes leading brands and falls back gracefully")
    void testExtractBrand() {
        assertEquals("Apple", AmazonPriceProvider.extractBrand("Apple iPhone 15 (128 GB) - Black"));
        assertEquals("Samsung", AmazonPriceProvider.extractBrand("Samsung Galaxy S24 Ultra 5G"));
        assertEquals("Sony", AmazonPriceProvider.extractBrand("Sony WH-1000XM5 Wireless Headphones"));
        assertEquals("boAt", AmazonPriceProvider.extractBrand("boAt Rockerz 450 Bluetooth On Ear Headphones"));
        assertEquals("OnePlus", AmazonPriceProvider.extractBrand("OnePlus 12 (Silky Black, 256 GB)"));
        assertEquals("Amazon", AmazonPriceProvider.extractBrand(null));
    }

    @Test
    @DisplayName("parseSearchResponse correctly parses RapidAPI Real-Time Amazon Data API JSON")
    void testParseSearchResponse() {
        String json = """
                {
                    "status": "OK",
                    "request_id": "test-req-123",
                    "data": {
                        "total_products": 2,
                        "country": "IN",
                        "domain": "www.amazon.in",
                        "products": [
                            {
                                "asin": "B0CHX2F5QT",
                                "product_title": "Apple iPhone 15 (128 GB) - Black",
                                "product_price": "₹58,999",
                                "product_original_price": "₹69,900",
                                "currency": "INR",
                                "product_star_rating": "4.5 out of 5 stars",
                                "product_num_ratings": 3200,
                                "product_url": "https://www.amazon.in/dp/B0CHX2F5QT",
                                "product_photo": "https://m.media-amazon.com/images/I/71657TiFeHL._SL1500_.jpg",
                                "is_best_seller": true
                            },
                            {
                                "asin": "B0CHX1W1XY",
                                "product_title": "Apple iPhone 15 (256 GB) - Blue",
                                "product_price": "₹68,999",
                                "currency": "INR",
                                "product_star_rating": "4.6",
                                "product_url": "https://www.amazon.in/dp/B0CHX1W1XY",
                                "product_photo": "https://m.media-amazon.com/images/I/71d7rfSl0wL._SL1500_.jpg"
                            },
                            {
                                "asin": "B0UNPRICED0",
                                "product_title": "Unpriced Dummy Item",
                                "product_price": null,
                                "product_url": "https://www.amazon.in/dp/B0UNPRICED0"
                            }
                        ]
                    }
                }
                """;

        List<ProviderProductDTO> products = configuredProvider.parseSearchResponse(json);

        assertNotNull(products);
        assertEquals(2, products.size(), "Unpriced item should be filtered out to preserve real prices only");

        ProviderProductDTO p1 = products.get(0);
        assertEquals("AMAZON", p1.getStore());
        assertEquals("B0CHX2F5QT", p1.getStoreProductId());
        assertEquals("Apple iPhone 15 (128 GB) - Black", p1.getTitle());
        assertEquals("Apple", p1.getBrand());
        assertEquals(58999.0, p1.getPrice());
        assertEquals("INR", p1.getCurrency());
        assertEquals("LIVE", p1.getStatus());
        assertEquals("IN_STOCK", p1.getAvailability());
        assertEquals(4.5, p1.getRating());
        assertEquals("https://www.amazon.in/dp/B0CHX2F5QT", p1.getProductUrl());
        assertEquals("https://m.media-amazon.com/images/I/71657TiFeHL._SL1500_.jpg", p1.getImageUrl());

        ProviderProductDTO p2 = products.get(1);
        assertEquals("B0CHX1W1XY", p2.getStoreProductId());
        assertEquals(68999.0, p2.getPrice());
        assertEquals(4.6, p2.getRating());
    }

    @Test
    @DisplayName("parseSearchResponse returns empty list on error status or invalid JSON")
    void testParseSearchResponseError() {
        String errorJson = """
                {
                    "status": "ERROR",
                    "message": "Invalid API key"
                }
                """;
        List<ProviderProductDTO> list = configuredProvider.parseSearchResponse(errorJson);
        assertNotNull(list);
        assertTrue(list.isEmpty());

        List<ProviderProductDTO> invalidJson = configuredProvider.parseSearchResponse("invalid json");
        assertNotNull(invalidJson);
        assertTrue(invalidJson.isEmpty());

        List<ProviderProductDTO> nullJson = configuredProvider.parseSearchResponse(null);
        assertNotNull(nullJson);
        assertTrue(nullJson.isEmpty());
    }
}
