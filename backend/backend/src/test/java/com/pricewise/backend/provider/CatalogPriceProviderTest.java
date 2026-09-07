package com.pricewise.backend.provider;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.provider.catalog.CatalogPriceProvider;
import com.pricewise.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CatalogPriceProviderTest {

    private ProductRepository productRepository;
    private CatalogPriceProvider catalogPriceProvider;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        catalogPriceProvider = new CatalogPriceProvider(productRepository);
    }

    @Test
    void testFetchCurrentPrice_ValidStoreProductId() {
        Product p = new Product();
        p.setId(1L);
        p.setCanonicalName("MacBook Air M2");
        p.setFlipkartPrice(71499.0);
        p.setAmazonPrice(72999.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        ProviderProductDTO result = catalogPriceProvider.fetchCurrentPrice("CAT-1", null);

        assertNotNull(result);
        assertEquals("CATALOG", result.getStore());
        assertEquals("CAT-1", result.getStoreProductId());
        assertEquals(71499.0, result.getPrice());
        assertEquals("SAMPLE_DATA", result.getStatus());
        assertEquals("IN_STOCK", result.getAvailability());
    }

    @Test
    void testFetchCurrentPrice_InvalidOrMissingIdReturnsNull() {
        assertNull(catalogPriceProvider.fetchCurrentPrice(null, null));
        assertNull(catalogPriceProvider.fetchCurrentPrice("INVALID-ID", null));

        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        assertNull(catalogPriceProvider.fetchCurrentPrice("CAT-999", null));
    }
}
