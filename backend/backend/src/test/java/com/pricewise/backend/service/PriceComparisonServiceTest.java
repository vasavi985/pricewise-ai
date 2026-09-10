package com.pricewise.backend.service;

import com.pricewise.backend.dto.PriceComparisonDTO;
import com.pricewise.backend.dto.StorePriceDTO;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.provider.PriceProvider;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PriceComparisonServiceTest {

    private PriceRecordRepository priceRecordRepository;
    private StoreProductRepository storeProductRepository;
    private ProviderManager providerManager;
    private PriceComparisonService priceComparisonService;

    @BeforeEach
    void setUp() {
        priceRecordRepository = Mockito.mock(PriceRecordRepository.class);
        storeProductRepository = Mockito.mock(StoreProductRepository.class);
        providerManager = Mockito.mock(ProviderManager.class);
        priceComparisonService = new PriceComparisonService(priceRecordRepository, storeProductRepository, providerManager);
    }

    @Test
    @DisplayName("Compare real prices strictly between Amazon and Flipkart")
    void testPriceComparisonCalculations() {
        Product product = new Product();
        product.setId(10L);
        product.setCanonicalName("Test Laptop");

        StoreProduct amazon = new StoreProduct(product, "AMAZON", "amz-1", "Test Laptop Amazon", "http://amazon.in/1", 60000.0, "INR", "IN_STOCK", "LIVE");
        StoreProduct flipkart = new StoreProduct(product, "FLIPKART", "flp-1", "Test Laptop Flipkart", "http://flipkart.com/1", 54000.0, "INR", "IN_STOCK", "LIVE");
        StoreProduct croma = new StoreProduct(product, "CROMA", "crm-1", "Test Laptop Croma", "http://croma.com/1", 58000.0, "INR", "IN_STOCK", "LIVE");
        StoreProduct unavail = new StoreProduct(product, "OTHER", "oth-1", "Test Laptop Other", "http://other.com/1", null, "INR", "UNAVAILABLE", "UNAVAILABLE");

        product.addStoreProduct(amazon);
        product.addStoreProduct(flipkart);
        product.addStoreProduct(croma);
        product.addStoreProduct(unavail);

        when(storeProductRepository.findByProductId(10L)).thenReturn(List.of(amazon, flipkart, croma, unavail));
        when(priceRecordRepository.findByProductIdOrderByCheckedAtAsc(10L)).thenReturn(Collections.emptyList());

        PriceComparisonDTO result = priceComparisonService.comparePrices(product);

        assertNotNull(result);
        assertEquals(54000.0, result.getLowestPrice(), 0.001);
        assertEquals(60000.0, result.getHighestPrice(), 0.001);
        assertEquals("FLIPKART", result.getBestStore());
        assertEquals(6000.0, result.getSavingsAmount(), 0.001);
        assertEquals(10.0, result.getSavingsPercentage(), 0.1);
        // Only Amazon (60000) and Flipkart (54000) are evaluated: average = 57000.0
        assertEquals(57000.0, result.getAveragePrice(), 0.1);

        // Verify only AMAZON and FLIPKART are in store comparison
        assertEquals(2, result.getStores().size());
        assertTrue(result.getStores().stream().allMatch(s -> "AMAZON".equalsIgnoreCase(s.getStore()) || "FLIPKART".equalsIgnoreCase(s.getStore())));
    }

    @Test
    @DisplayName("When Amazon is configured in ProviderManager, missing store entry reports UNAVAILABLE and no-match title, not CONFIG_REQUIRED")
    void testAmazonReportsUnavailableWhenConfigured() {
        Product product = new Product();
        product.setId(20L);
        product.setCanonicalName("Samsung Galaxy S24");

        StoreProduct flipkart = new StoreProduct(product, "FLIPKART", "flp-20", "Samsung Galaxy S24 Flipkart", "http://flipkart.com/20", 64999.0, "INR", "IN_STOCK", "LIVE");
        when(storeProductRepository.findByProductId(20L)).thenReturn(List.of(flipkart));
        when(priceRecordRepository.findByProductIdOrderByCheckedAtAsc(20L)).thenReturn(Collections.emptyList());

        PriceProvider mockAmazon = Mockito.mock(PriceProvider.class);
        when(mockAmazon.isConfigured()).thenReturn(true);
        when(mockAmazon.getStoreStatus()).thenReturn("LIVE");
        when(providerManager.getProvider("AMAZON")).thenReturn(mockAmazon);

        PriceComparisonDTO result = priceComparisonService.comparePrices(product);
        assertNotNull(result);

        StorePriceDTO amazonDto = result.getStores().stream()
                .filter(s -> "AMAZON".equalsIgnoreCase(s.getStore()))
                .findFirst()
                .orElse(null);

        assertNotNull(amazonDto);
        assertEquals("Amazon (No matching result)", amazonDto.getTitle());
        assertEquals("UNAVAILABLE", amazonDto.getStatus(), "Should not report CONFIG_REQUIRED when provider is configured");
        assertEquals("UNAVAILABLE", amazonDto.getAvailability());
        assertNull(amazonDto.getPrice());

        // When only one valid store exists, savings is null (not shown)
        assertNull(result.getSavingsAmount());
    }

    @Test
    @DisplayName("When Amazon is NOT configured in ProviderManager, missing store entry reports CONFIG_REQUIRED")
    void testAmazonReportsConfigRequiredWhenUnconfigured() {
        Product product = new Product();
        product.setId(30L);
        product.setCanonicalName("iPhone 15");

        StoreProduct flipkart = new StoreProduct(product, "FLIPKART", "flp-30", "iPhone 15 Flipkart", "http://flipkart.com/30", 59999.0, "INR", "IN_STOCK", "LIVE");
        when(storeProductRepository.findByProductId(30L)).thenReturn(List.of(flipkart));
        when(priceRecordRepository.findByProductIdOrderByCheckedAtAsc(30L)).thenReturn(Collections.emptyList());

        PriceProvider mockAmazon = Mockito.mock(PriceProvider.class);
        when(mockAmazon.isConfigured()).thenReturn(false);
        when(mockAmazon.getStoreStatus()).thenReturn("CONFIG_REQUIRED");
        when(providerManager.getProvider("AMAZON")).thenReturn(mockAmazon);

        PriceComparisonDTO result = priceComparisonService.comparePrices(product);
        assertNotNull(result);

        StorePriceDTO amazonDto = result.getStores().stream()
                .filter(s -> "AMAZON".equalsIgnoreCase(s.getStore()))
                .findFirst()
                .orElse(null);

        assertNotNull(amazonDto);
        assertEquals("Amazon (API Key Required)", amazonDto.getTitle());
        assertEquals("CONFIG_REQUIRED", amazonDto.getStatus());
        assertEquals("UNAVAILABLE", amazonDto.getAvailability());
        assertNull(amazonDto.getPrice());
    }

    @Test
    @DisplayName("When Flipkart is NOT configured in ProviderManager, missing store entry reports CONFIG_REQUIRED")
    void testFlipkartReportsConfigRequiredWhenUnconfigured() {
        Product product = new Product();
        product.setId(40L);
        product.setCanonicalName("MacBook Air");

        StoreProduct amazon = new StoreProduct(product, "AMAZON", "amz-40", "MacBook Air Amazon", "http://amazon.in/40", 72000.0, "INR", "IN_STOCK", "LIVE");
        when(storeProductRepository.findByProductId(40L)).thenReturn(List.of(amazon));
        when(priceRecordRepository.findByProductIdOrderByCheckedAtAsc(40L)).thenReturn(Collections.emptyList());

        PriceProvider mockFlipkart = Mockito.mock(PriceProvider.class);
        when(mockFlipkart.isConfigured()).thenReturn(false);
        when(mockFlipkart.getStoreStatus()).thenReturn("CONFIG_REQUIRED");
        when(providerManager.getProvider("FLIPKART")).thenReturn(mockFlipkart);

        PriceComparisonDTO result = priceComparisonService.comparePrices(product);
        assertNotNull(result);

        StorePriceDTO flipkartDto = result.getStores().stream()
                .filter(s -> "FLIPKART".equalsIgnoreCase(s.getStore()))
                .findFirst()
                .orElse(null);

        assertNotNull(flipkartDto);
        assertEquals("Flipkart (API Key Required)", flipkartDto.getTitle());
        assertEquals("CONFIG_REQUIRED", flipkartDto.getStatus());
        assertNull(result.getSavingsAmount());
    }
}
