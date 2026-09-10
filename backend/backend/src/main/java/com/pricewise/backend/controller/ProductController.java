package com.pricewise.backend.controller;

import com.pricewise.backend.dto.*;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.UserSearchHistory;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.repository.UserSearchHistoryRepository;
import com.pricewise.backend.service.FirebaseAuthService;
import com.pricewise.backend.service.PriceComparisonService;
import com.pricewise.backend.service.PriceHistoryService;
import com.pricewise.backend.service.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.pricewise.backend.provider.PriceProvider;
import com.pricewise.backend.provider.flipkart.FlipkartPriceProvider;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final PriceComparisonService priceComparisonService;
    private final PriceHistoryService priceHistoryService;
    private final ProviderManager providerManager;
    private final UserSearchHistoryRepository historyRepository;
    private final FirebaseAuthService firebaseAuthService;

    public ProductController(ProductRepository productRepository,
                             ProductSearchService productSearchService,
                             PriceComparisonService priceComparisonService,
                             PriceHistoryService priceHistoryService,
                             ProviderManager providerManager,
                             @Autowired(required = false) UserSearchHistoryRepository historyRepository,
                             @Autowired(required = false) FirebaseAuthService firebaseAuthService) {
        this.productRepository = productRepository;
        this.productSearchService = productSearchService;
        this.priceComparisonService = priceComparisonService;
        this.priceHistoryService = priceHistoryService;
        this.providerManager = providerManager;
        this.historyRepository = historyRepository;
        this.firebaseAuthService = firebaseAuthService;
    }

    // Legacy endpoint for backward compatibility
    @GetMapping
    public List<Product> getAllProducts() {
        productSearchService.syncLegacyProducts();
        return productRepository.findAll();
    }

    // Unified multi-store search endpoint
    @GetMapping("/search")
    public ResponseEntity<ProductSearchResultDTO> searchProducts(
            @RequestParam(value = "query", required = false, defaultValue = "") String query,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ProductSearchResultDTO result = productSearchService.search(query);

        // Record search history if user is authenticated and searched a specific query
        if (query != null && !query.trim().isEmpty() && authHeader != null && historyRepository != null && firebaseAuthService != null) {
            try {
                String uid = firebaseAuthService.extractUidOrNull(authHeader);
                if (uid != null) {
                    // Ensure exactly one history record per search: prevent duplicate from rapid re-renders or StrictMode
                    List<UserSearchHistory> recent = historyRepository.findByUserId(uid);
                    boolean isDuplicate = false;
                    if (recent != null && !recent.isEmpty()) {
                        UserSearchHistory latest = recent.get(0);
                        if (query.trim().equalsIgnoreCase(latest.getQuery()) &&
                                latest.getCreatedAt() != null &&
                                java.time.Duration.between(latest.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds() < 5) {
                            isDuplicate = true;
                        }
                    }

                    if (!isDuplicate) {
                        UserSearchHistory history = new UserSearchHistory();
                        history.setUserId(uid);
                        history.setQuery(query.trim());
                        history.setResultCount(result.getTotalFound());

                        if (result.getResults() != null && !result.getResults().isEmpty()) {
                            PriceComparisonDTO top = result.getResults().get(0);
                            history.setTopProductName(top.getProductName());
                            history.setTopProductPrice(top.getLowestPrice());
                            history.setTopProductStore(top.getBestStore());
                            history.setTopProductImage(top.getImageUrl());
                            history.setTopProductId(top.getProductId());
                        }
                        historyRepository.save(history);
                    }
                }
            } catch (Exception e) {
                // Non-blocking log
            }
        }

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

    // Diagnostic endpoint to inspect live Flipkart endpoints and schema
    @GetMapping("/diagnose-flipkart")
    public ResponseEntity<Map<String, Object>> diagnoseFlipkart(
            @RequestParam(value = "query", required = false, defaultValue = "Samsung Galaxy S24") String query,
            @RequestParam(value = "endpoint", required = false) String endpoint) {
        PriceProvider provider = providerManager.getProvider("FLIPKART");
        if (provider instanceof FlipkartPriceProvider flipkart) {
            if (endpoint != null && !endpoint.trim().isEmpty()) {
                return ResponseEntity.ok(flipkart.testEndpoint(endpoint.trim()));
            }
            return ResponseEntity.ok(flipkart.diagnoseProvider(query));
        }
        return ResponseEntity.ok(Map.of("error", "Flipkart provider not found"));
    }
}