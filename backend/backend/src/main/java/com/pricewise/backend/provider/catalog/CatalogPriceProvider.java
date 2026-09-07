package com.pricewise.backend.provider.catalog;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.provider.PriceProvider;
import com.pricewise.backend.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogPriceProvider implements PriceProvider {

    private final ProductRepository productRepository;

    public CatalogPriceProvider(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public String getStoreName() {
        return "CATALOG";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String getStoreStatus() {
        return "SAMPLE_DATA";
    }

    @Override
    public String getRequiredConfig() {
        return "None (Verified Database Catalog)";
    }

    @Override
    public String getDescription() {
        return "Pricewise Verified Product Catalog (Cloud Firestore)";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        List<Product> products = (query == null || query.trim().isEmpty() || query.equalsIgnoreCase("all"))
                ? productRepository.findAll()
                : productRepository.searchProducts(query.trim());

        List<ProviderProductDTO> results = new ArrayList<>();

        for (Product p : products) {
            String canonicalName = p.getCanonicalName() != null ? p.getCanonicalName() : p.getProductName();
            Double benchmarkPrice = p.getFlipkartPrice() != null ? p.getFlipkartPrice()
                    : (p.getAmazonPrice() != null ? p.getAmazonPrice() : p.getCromaPrice());

            if (benchmarkPrice == null || benchmarkPrice <= 0) continue;

            results.add(new ProviderProductDTO(
                    "CATALOG",
                    "CAT-" + p.getId(),
                    canonicalName + " (Catalog Benchmark)",
                    canonicalName,
                    p.getBrand() != null ? p.getBrand() : extractBrand(canonicalName),
                    p.getModel(),
                    p.getCategory() != null ? p.getCategory() : "Electronics",
                    p.getDescription() != null ? p.getDescription() : "Verified reference listing from local catalog",
                    p.getImageUrl() != null ? p.getImageUrl() : defaultImage(canonicalName),
                    null, // No fake external URL
                    benchmarkPrice,
                    "INR",
                    "IN_STOCK",
                    "SAMPLE_DATA",
                    p.getRating() != null ? p.getRating() : 4.5
            ));
        }

        return results;
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (storeProductId == null) return null;
        try {
            Long productId = Long.parseLong(storeProductId.replace("CAT-", ""));
            return productRepository.findById(productId).map(p -> {
                String canonicalName = p.getCanonicalName() != null ? p.getCanonicalName() : p.getProductName();
                Double benchmarkPrice = p.getFlipkartPrice() != null ? p.getFlipkartPrice()
                        : (p.getAmazonPrice() != null ? p.getAmazonPrice() : p.getCromaPrice());
                if (benchmarkPrice == null || benchmarkPrice <= 0) return null;

                return new ProviderProductDTO(
                        "CATALOG",
                        storeProductId,
                        canonicalName + " (Catalog Benchmark)",
                        canonicalName,
                        p.getBrand() != null ? p.getBrand() : extractBrand(canonicalName),
                        p.getModel(),
                        p.getCategory() != null ? p.getCategory() : "Electronics",
                        p.getDescription() != null ? p.getDescription() : "Verified reference listing from local catalog",
                        p.getImageUrl() != null ? p.getImageUrl() : defaultImage(canonicalName),
                        null,
                        benchmarkPrice,
                        "INR",
                        "IN_STOCK",
                        "SAMPLE_DATA",
                        p.getRating() != null ? p.getRating() : 4.5
                );
            }).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBrand(String name) {
        if (name == null) return "Generic";
        String lower = name.toLowerCase();
        if (lower.contains("apple") || lower.contains("macbook") || lower.contains("iphone")) return "Apple";
        if (lower.contains("samsung")) return "Samsung";
        if (lower.contains("sony")) return "Sony";
        if (lower.contains("dell")) return "Dell";
        if (lower.contains("hp")) return "HP";
        return "Electronics";
    }

    private String defaultImage(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("macbook")) {
            return "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80";
        }
        if (lower.contains("iphone")) {
            return "https://images.unsplash.com/photo-1592750475338-74b7b21085ab?auto=format&fit=crop&w=600&q=80";
        }
        if (lower.contains("samsung")) {
            return "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=600&q=80";
        }
        return "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
    }
}
