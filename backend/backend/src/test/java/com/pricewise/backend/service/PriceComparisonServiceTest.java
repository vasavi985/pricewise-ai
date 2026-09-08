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
        assertEquals(57333.33, result.getAveragePrice(), 0.1);
    }

    @Test
    @DisplayName("When Amazon is configured in ProviderManager, missing store entry reports UNAVAILABLE and Amazon India title, not CONFIG_REQUIRED")
    void testAmazonReportsUnavailableWhenConfigured() {
        Product product = new Product();
        product.setId(20L);
        product.setCanonicalName("Samsung Galaxy S24");

        StoreProduct catalog = new StoreProduct(product, "CATALOG", "cat-20", "Samsung Galaxy S24 (Catalog)", null, 64999.0, "INR", "IN_STOCK", "SAMPLE_DATA");
        when(storeProductRepository.findByProductId(20L)).thenReturn(List.of(catalog));
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
        assertEquals("Amazon India", amazonDto.getTitle());
        assertEquals("UNAVAILABLE", amazonDto.getStatus(), "Should not report CONFIG_REQUIRED when provider is configured");
        assertEquals("UNAVAILABLE", amazonDto.getAvailability());
        assertNull(amazonDto.getPrice());
    }

    @Test
    @DisplayName("When Amazon is NOT configured in ProviderManager, missing store entry reports CONFIG_REQUIRED")
    void testAmazonReportsConfigRequiredWhenUnconfigured() {
        Product product = new Product();
        product.setId(30L);
        product.setCanonicalName("iPhone 15");

        StoreProduct catalog = new StoreProduct(product, "CATALOG", "cat-30", "iPhone 15 (Catalog)", null, 59999.0, "INR", "IN_STOCK", "SAMPLE_DATA");
        when(storeProductRepository.findByProductId(30L)).thenReturn(List.of(catalog));
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
        assertEquals("CONFIG_REQUIRED", amazonDto.getStatus());
        assertEquals("Amazon India (Config Required)", amazonDto.getTitle());
    }
}
