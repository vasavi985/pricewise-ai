package com.pricewise.backend.service;

import com.pricewise.backend.dto.PriceComparisonDTO;
import com.pricewise.backend.dto.ProductSearchResultDTO;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.ProductRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProviderManager providerManager;
    private final ProductRepository productRepository;
    private final StoreProductRepository storeProductRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final PriceComparisonService priceComparisonService;

    public ProductSearchService(ProviderManager providerManager,
                                ProductRepository productRepository,
                                StoreProductRepository storeProductRepository,
                                PriceRecordRepository priceRecordRepository,
                                PriceComparisonService priceComparisonService) {
        this.providerManager = providerManager;
        this.productRepository = productRepository;
        this.storeProductRepository = storeProductRepository;
        this.priceRecordRepository = priceRecordRepository;
        this.priceComparisonService = priceComparisonService;
    }

    public ProductSearchResultDTO search(String query) {
        String safeQuery = (query != null) ? query.trim() : "";
        log.info("Executing unified multi-provider search for: '{}'", safeQuery);

        // 1. Synchronize any legacy products to ensure StoreProduct and PriceRecords exist
        syncLegacyProducts();

        // 2. Fetch from all registered providers
        List<ProviderProductDTO> providerResults = providerManager.searchAll(safeQuery);

        // 3. Ingest and match results to persistent Product entities
        Set<Long> matchedProductIds = new LinkedHashSet<>();

        for (ProviderProductDTO item : providerResults) {
            Product product = findOrCreateProduct(item);
            StoreProduct storeProduct = findOrCreateStoreProduct(product, item);

            // If price changed or no price record exists, append an immutable PriceRecord
            recordPriceIfChanged(storeProduct, item.getPrice(), item.getStatus());

            matchedProductIds.add(product.getId());
        }

        // Also include all database products if query is empty, or matching products if query is non-empty
        if (safeQuery.isEmpty()) {
            List<Product> allDb = productRepository.findAll();
            for (Product p : allDb) {
                matchedProductIds.add(p.getId());
            }
        } else {
            List<Product> dbMatches = productRepository.searchProducts(safeQuery);
            for (Product p : dbMatches) {
                matchedProductIds.add(p.getId());
            }
        }

        // 4. Build comparative DTOs
        List<PriceComparisonDTO> comparisons = new ArrayList<>();
        for (Long pid : matchedProductIds) {
            productRepository.findById(pid).ifPresent(p -> comparisons.add(priceComparisonService.comparePrices(p)));
        }

        return new ProductSearchResultDTO(safeQuery, comparisons, providerManager.getProviderStatuses());
    }

    public void syncLegacyProducts() {
        List<Product> all = productRepository.findAll();
        for (Product p : all) {
            String canonicalName = p.getCanonicalName() != null ? p.getCanonicalName() : p.getProductName();
            if (p.getCanonicalName() == null) {
                p.setCanonicalName(canonicalName);
            }

            Double benchmarkPrice = p.getFlipkartPrice() != null ? p.getFlipkartPrice()
                    : (p.getAmazonPrice() != null ? p.getAmazonPrice() : p.getCromaPrice());

            if (benchmarkPrice != null && benchmarkPrice > 0) {
                ensureCatalogStoreProduct(p, benchmarkPrice);
            }

            if (p.getImageUrl() == null) {
                p.setImageUrl(getDefaultImage(canonicalName));
            }
            productRepository.save(p);
        }
    }

    private void ensureCatalogStoreProduct(Product product, Double price) {
        Optional<StoreProduct> existing = storeProductRepository.findByProductIdAndStore(product.getId(), "CATALOG");
        StoreProduct sp;
        String canonical = product.getCanonicalName() != null ? product.getCanonicalName() : product.getProductName();
        if (existing.isEmpty()) {
            sp = new StoreProduct(
                    product,
                    "CATALOG",
                    "CAT-" + product.getId(),
                    canonical + " (Catalog Benchmark)",
                    null, // No fake external URL
                    price,
                    "INR",
                    "IN_STOCK",
                    "SAMPLE_DATA"
            );
            sp.setImageUrl(product.getImageUrl() != null ? product.getImageUrl() : getDefaultImage(canonical));
            sp = storeProductRepository.save(sp);

            recordInitialPrice(sp, price, "SAMPLE_DATA");
        } else {
            sp = existing.get();
            if ("FETCH_FAILED".equals(sp.getStatus())) {
                sp.setStatus("SAMPLE_DATA");
                storeProductRepository.save(sp);
            }
            recordInitialPrice(sp, price, "SAMPLE_DATA");
        }
    }

    private void recordInitialPrice(StoreProduct sp, Double basePrice, String status) {
        if (basePrice == null || basePrice <= 0) return;
        if (priceRecordRepository.findFirstByStoreProductIdOrderByCheckedAtDesc(sp.getId()).isEmpty()) {
            PriceRecord record = new PriceRecord(sp, basePrice, sp.getCurrency(), sp.getAvailability(), status, "INITIAL_CATALOG");
            priceRecordRepository.save(record);
        }
    }

    private Product findOrCreateProduct(ProviderProductDTO item) {
        String canonical = item.getCanonicalName() != null ? item.getCanonicalName().trim() : item.getTitle().trim();

        Optional<Product> existing = productRepository.findByCanonicalNameIgnoreCase(canonical);
        if (existing.isEmpty()) {
            existing = productRepository.findByProductNameIgnoreCase(canonical);
        }

        if (existing.isEmpty()) {
            List<Product> allProducts = productRepository.findAll();
            String lowerCanonical = canonical.toLowerCase();
            for (Product p : allProducts) {
                String pName = (p.getCanonicalName() != null ? p.getCanonicalName() : p.getProductName()).toLowerCase();
                if (!pName.isEmpty() && (lowerCanonical.contains(pName) || pName.contains(lowerCanonical))) {
                    existing = Optional.of(p);
                    break;
                }
            }
        }

        if (existing.isPresent()) {
            Product p = existing.get();
            if (p.getImageUrl() == null && item.getImageUrl() != null) {
                p.setImageUrl(item.getImageUrl());
                productRepository.save(p);
            }
            return p;
        }

        Product newProduct = new Product();
        newProduct.setCanonicalName(canonical);
        newProduct.setProductName(canonical);
        newProduct.setBrand(item.getBrand());
        newProduct.setModel(item.getModel());
        newProduct.setCategory(item.getCategory());
        newProduct.setDescription(item.getDescription());
        newProduct.setImageUrl(item.getImageUrl() != null ? item.getImageUrl() : getDefaultImage(canonical));
        newProduct.setRating(item.getRating());

        return productRepository.save(newProduct);
    }

    private StoreProduct findOrCreateStoreProduct(Product product, ProviderProductDTO item) {
        Optional<StoreProduct> existing = storeProductRepository.findByProductIdAndStore(product.getId(), item.getStore());

        StoreProduct sp;
        if (existing.isPresent()) {
            sp = existing.get();
            sp.setProduct(product);
            sp.setCurrentPrice(item.getPrice());
            sp.setAvailability(item.getAvailability());
            sp.setStatus(item.getStatus());
            sp.setLastCheckedAt(LocalDateTime.now());
            if (item.getProductUrl() != null) sp.setProductUrl(item.getProductUrl());
            if (item.getImageUrl() != null) sp.setImageUrl(item.getImageUrl());
        } else {
            sp = new StoreProduct(
                    product,
                    item.getStore(),
                    item.getStoreProductId(),
                    item.getTitle(),
                    item.getProductUrl(),
                    item.getPrice(),
                    item.getCurrency(),
                    item.getAvailability(),
                    item.getStatus()
            );
            sp.setImageUrl(item.getImageUrl());
        }

        return storeProductRepository.save(sp);
    }

    private void recordPriceIfChanged(StoreProduct storeProduct, Double newPrice, String status) {
        if (newPrice == null || newPrice <= 0) return;

        Optional<PriceRecord> latest = priceRecordRepository.findFirstByStoreProductIdOrderByCheckedAtDesc(storeProduct.getId());

        if (latest.isEmpty() || Math.abs(latest.get().getPrice() - newPrice) > 0.01) {
            PriceRecord record = new PriceRecord(storeProduct, newPrice, storeProduct.getCurrency(), storeProduct.getAvailability(), status);
            priceRecordRepository.save(record);
            String prodName = storeProduct.getProduct() != null ? storeProduct.getProduct().getCanonicalName() : storeProduct.getTitle();
            log.info("Recorded price change for [{}] store [{}] -> ₹{}", prodName, storeProduct.getStore(), newPrice);
        }
    }

    private String getDefaultImage(String name) {
        if (name == null) return "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
        String lower = name.toLowerCase();
        if (lower.contains("macbook") || lower.contains("laptop")) {
            return "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80";
        }
        if (lower.contains("iphone") || lower.contains("phone") || lower.contains("galaxy")) {
            return "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?auto=format&fit=crop&w=600&q=80";
        }
        return "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
    }
}
