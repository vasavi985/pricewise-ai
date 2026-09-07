package com.pricewise.backend.controller;

import com.pricewise.backend.dto.*;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.service.PriceComparisonService;
import com.pricewise.backend.service.PriceHistoryService;
import com.pricewise.backend.service.ProductSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final PriceComparisonService priceComparisonService;
    private final PriceHistoryService priceHistoryService;
    private final ProviderManager providerManager;

    public ProductController(ProductRepository productRepository,
                             ProductSearchService productSearchService,
                             PriceComparisonService priceComparisonService,
                             PriceHistoryService priceHistoryService,
                             ProviderManager providerManager) {
        this.productRepository = productRepository;
        this.productSearchService = productSearchService;
        this.priceComparisonService = priceComparisonService;
        this.priceHistoryService = priceHistoryService;
        this.providerManager = providerManager;
    }

    // Legacy endpoint for backward compatibility
    @GetMapping
    public List<Product> getAllProducts() {
        productSearchService.syncLegacyProducts();
        return productRepository.findAll();
    }

    // Unified multi-store search endpoint
    @GetMapping("/search")
    public ResponseEntity<ProductSearchResultDTO> searchProducts(@RequestParam(value = "query", required = false, defaultValue = "") String query) {
        ProductSearchResultDTO result = productSearchService.search(query);
        return ResponseEntity.ok(result);
    }

    // Single product details with comparative store listings
    @GetMapping("/{id}")
    public ResponseEntity<PriceComparisonDTO> getProductDetails(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(product -> ResponseEntity.ok(priceComparisonService.comparePrices(product)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Store comparison prices for a specific product
    @GetMapping("/{id}/prices")
    public ResponseEntity<PriceComparisonDTO> getProductPrices(@PathVariable Long id) {
        return getProductDetails(id);
    }

    // Historical price points and trends for Recharts
    @GetMapping("/{id}/history")
    public ResponseEntity<PriceHistoryDTO> getProductHistory(@PathVariable Long id) {
        try {
            PriceHistoryDTO history = priceHistoryService.getHistory(id);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Connected store provider status inspection
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderStatusDTO>> getProviderStatuses() {
        return ResponseEntity.ok(providerManager.getProviderStatuses());
    }
}