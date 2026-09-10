package com.pricewise.backend.service;

import com.pricewise.backend.dto.PriceComparisonDTO;
import com.pricewise.backend.dto.ProductSearchResultDTO;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.dto.ProviderStatusDTO;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    private ProviderManager providerManager;
    private ProductRepository productRepository;
    private StoreProductRepository storeProductRepository;
    private PriceRecordRepository priceRecordRepository;
    private PriceComparisonService priceComparisonService;
    private ProductSearchService productSearchService;

    @BeforeEach
    void setUp() {
        providerManager = Mockito.mock(ProviderManager.class);
        productRepository = Mockito.mock(ProductRepository.class);
        storeProductRepository = Mockito.mock(StoreProductRepository.class);
        priceRecordRepository = Mockito.mock(PriceRecordRepository.class);
        priceComparisonService = Mockito.mock(PriceComparisonService.class);

        productSearchService = new ProductSearchService(
                providerManager,
                productRepository,
                storeProductRepository,
                priceRecordRepository,
                priceComparisonService
        );
    }

    @Test
    @DisplayName("When Amazon times out, real Flipkart results are preserved and returned")
    void testFlipkartPreservedWhenAmazonTimesOut() {
        ProviderProductDTO flipkartProduct = new ProviderProductDTO(
                "FLIPKART",
                "MOB12345",
                "Samsung Galaxy S24 Ultra",
                "Samsung Galaxy S24 Ultra",
                "Samsung",
                "S24 Ultra",
                "Smartphones",
                "Flagship phone",
                "http://img.com/s24.jpg",
                "https://flipkart.com/s24",
                89999.0,
                "INR",
                "IN_STOCK",
                "LIVE",
                4.8
        );

        when(providerManager.searchAll(eq("Samsung Galaxy S24"))).thenReturn(List.of(flipkartProduct));

        Product product = new Product();
        product.setId(101L);
        product.setCanonicalName("Samsung Galaxy S24 Ultra");
        product.setProductName("Samsung Galaxy S24 Ultra");

        when(productRepository.findByCanonicalNameIgnoreCase(any())).thenReturn(Optional.of(product));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        when(storeProductRepository.findByProductIdAndStore(101L, "FLIPKART")).thenReturn(Optional.empty());
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(i -> i.getArgument(0));

        PriceComparisonDTO comparisonDTO = new PriceComparisonDTO();
        comparisonDTO.setProductId(101L);
        comparisonDTO.setProductName("Samsung Galaxy S24 Ultra");
        comparisonDTO.setLowestPrice(89999.0);
        comparisonDTO.setBestStore("FLIPKART");

        when(priceComparisonService.comparePrices(product)).thenReturn(comparisonDTO);
        when(providerManager.getProviderStatuses()).thenReturn(List.of(
                new ProviderStatusDTO("AMAZON", true, "UNAVAILABLE", "Amazon API", ""),
                new ProviderStatusDTO("FLIPKART", true, "LIVE", "Flipkart API", "")
        ));

        ProductSearchResultDTO result = productSearchService.search("Samsung Galaxy S24");

        assertNotNull(result);
        assertEquals(1, result.getTotalFound(), "Flipkart product should be found and returned even if Amazon times out");
        assertEquals("Samsung Galaxy S24 Ultra", result.getResults().get(0).getProductName());
        assertEquals("FLIPKART", result.getResults().get(0).getBestStore());

        assertTrue(result.getProviders().stream().anyMatch(p -> "AMAZON".equals(p.getStore()) && "UNAVAILABLE".equals(p.getStatus())));
        assertTrue(result.getProviders().stream().anyMatch(p -> "FLIPKART".equals(p.getStore()) && "LIVE".equals(p.getStatus())));
    }

    @Test
    @DisplayName("When Flipkart times out, real Amazon results are preserved and returned")
    void testAmazonPreservedWhenFlipkartTimesOut() {
        ProviderProductDTO amazonProduct = new ProviderProductDTO(
                "AMAZON",
                "B0CHX2F5QT",
                "iPhone 15",
                "iPhone 15",
                "Apple",
                "iPhone 15",
                "Smartphones",
                "Apple smartphone",
                "http://img.com/iphone.jpg",
                "https://amazon.in/dp/B0CHX2F5QT",
                69990.0,
                "INR",
                "IN_STOCK",
                "LIVE",
                4.7
        );

        when(providerManager.searchAll(eq("iPhone 15"))).thenReturn(List.of(amazonProduct));

        Product product = new Product();
        product.setId(201L);
        product.setCanonicalName("iPhone 15");
        product.setProductName("iPhone 15");

        when(productRepository.findByCanonicalNameIgnoreCase(any())).thenReturn(Optional.of(product));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(201L)).thenReturn(Optional.of(product));
        when(storeProductRepository.findByProductIdAndStore(201L, "AMAZON")).thenReturn(Optional.empty());
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(i -> i.getArgument(0));

        PriceComparisonDTO comparisonDTO = new PriceComparisonDTO();
        comparisonDTO.setProductId(201L);
        comparisonDTO.setProductName("iPhone 15");
        comparisonDTO.setLowestPrice(69990.0);
        comparisonDTO.setBestStore("AMAZON");

        when(priceComparisonService.comparePrices(product)).thenReturn(comparisonDTO);
        when(providerManager.getProviderStatuses()).thenReturn(List.of(
                new ProviderStatusDTO("AMAZON", true, "LIVE", "Amazon API", ""),
                new ProviderStatusDTO("FLIPKART", true, "UNAVAILABLE", "Flipkart API", "")
        ));

        ProductSearchResultDTO result = productSearchService.search("iPhone 15");

        assertNotNull(result);
        assertEquals(1, result.getTotalFound());
        assertEquals("iPhone 15", result.getResults().get(0).getProductName());
        assertEquals("AMAZON", result.getResults().get(0).getBestStore());
    }

    @Test
    @DisplayName("When both Amazon and Flipkart return nothing, search returns an honest empty state without fabricating products")
    void testBothProvidersEmptyReturnsHonestEmptyState() {
        when(providerManager.searchAll(eq("Unknown Gadget"))).thenReturn(Collections.emptyList());
        when(productRepository.searchProducts(eq("Unknown Gadget"))).thenReturn(Collections.emptyList());
        when(providerManager.getProviderStatuses()).thenReturn(List.of(
                new ProviderStatusDTO("AMAZON", true, "LIVE", "Amazon API", ""),
                new ProviderStatusDTO("FLIPKART", true, "LIVE", "Flipkart API", "")
        ));

        ProductSearchResultDTO result = productSearchService.search("Unknown Gadget");

        assertNotNull(result);
        assertEquals(0, result.getTotalFound());
        assertTrue(result.getResults().isEmpty());
    }
}
