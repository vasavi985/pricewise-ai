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
    @DisplayName("When Amazon times out and returns empty, Open Commerce and Catalog results are preserved")
    void testOpenCommerceAndCatalogPreservedWhenAmazonTimesOut() {
        // Given Open Commerce returns a live product and Amazon returns nothing (timed out)
        ProviderProductDTO openCommerceProduct = new ProviderProductDTO(
                "OPEN_COMMERCE",
                "LIVE-101",
                "Samsung Galaxy S24 Ultra",
                "Samsung Galaxy S24 Ultra",
                "Samsung",
                "S24 Ultra",
                "Smartphones",
                "Flagship phone",
                "http://img.com/s24.jpg",
                "http://store.com/s24",
                89999.0,
                "INR",
                "IN_STOCK",
                "LIVE",
                4.8
        );

        when(providerManager.searchAll(eq("Samsung Galaxy S24"))).thenReturn(List.of(openCommerceProduct));

        Product product = new Product();
        product.setId(101L);
        product.setCanonicalName("Samsung Galaxy S24 Ultra");
        product.setProductName("Samsung Galaxy S24 Ultra");

        when(productRepository.findByCanonicalNameIgnoreCase(any())).thenReturn(Optional.of(product));
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        when(storeProductRepository.findByProductIdAndStore(101L, "OPEN_COMMERCE")).thenReturn(Optional.empty());
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(i -> i.getArgument(0));

        PriceComparisonDTO comparisonDTO = new PriceComparisonDTO();
        comparisonDTO.setProductId(101L);
        comparisonDTO.setProductName("Samsung Galaxy S24 Ultra");
        comparisonDTO.setLowestPrice(89999.0);
        comparisonDTO.setBestStore("OPEN_COMMERCE");

        when(priceComparisonService.comparePrices(product)).thenReturn(comparisonDTO);
        when(providerManager.getProviderStatuses()).thenReturn(List.of(
                new ProviderStatusDTO("AMAZON", true, "UNAVAILABLE", "Amazon API", ""),
                new ProviderStatusDTO("OPEN_COMMERCE", true, "LIVE", "Open Commerce API", ""),
                new ProviderStatusDTO("CATALOG", true, "SAMPLE_DATA", "Catalog", "")
        ));

        ProductSearchResultDTO result = productSearchService.search("Samsung Galaxy S24");

        assertNotNull(result);
        assertEquals(1, result.getTotalFound(), "Open Commerce product should still be found and returned");
        assertEquals("Samsung Galaxy S24 Ultra", result.getResults().get(0).getProductName());
        assertEquals("OPEN_COMMERCE", result.getResults().get(0).getBestStore());

        // Verify provider statuses still report accurate states
        assertTrue(result.getProviders().stream().anyMatch(p -> "AMAZON".equals(p.getStore()) && "UNAVAILABLE".equals(p.getStatus())));
        assertTrue(result.getProviders().stream().anyMatch(p -> "OPEN_COMMERCE".equals(p.getStore()) && "LIVE".equals(p.getStatus())));
    }
}
