package com.pricewise.backend.service;

import com.pricewise.backend.dto.PriceComparisonDTO;
import com.pricewise.backend.dto.StorePriceDTO;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import com.pricewise.backend.provider.PriceProvider;
import com.pricewise.backend.provider.ProviderManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class PriceComparisonService {

    private final PriceRecordRepository priceRecordRepository;
    private final StoreProductRepository storeProductRepository;
    private final ProviderManager providerManager;

    public PriceComparisonService(PriceRecordRepository priceRecordRepository, StoreProductRepository storeProductRepository) {
        this(priceRecordRepository, storeProductRepository, null);
    }

    @Autowired
    public PriceComparisonService(PriceRecordRepository priceRecordRepository,
                                  StoreProductRepository storeProductRepository,
                                  @Autowired(required = false) ProviderManager providerManager) {
        this.priceRecordRepository = priceRecordRepository;
        this.storeProductRepository = storeProductRepository;
        this.providerManager = providerManager;
    }

    public PriceComparisonDTO comparePrices(Product product) {
        PriceComparisonDTO dto = new PriceComparisonDTO();
        dto.setProductId(product.getId());
        dto.setProductName(product.getCanonicalName() != null ? product.getCanonicalName() : product.getProductName());
        dto.setBrand(product.getBrand());
        dto.setCategory(product.getCategory());
        dto.setDescription(product.getDescription());
        dto.setImageUrl(product.getImageUrl());
        dto.setRating(product.getRating());

        List<StoreProduct> storeProducts = null;
        if (storeProductRepository != null && product.getId() != null) {
            storeProducts = storeProductRepository.findByProductId(product.getId());
        }
        if (storeProducts == null || storeProducts.isEmpty()) {
            storeProducts = product.getStoreProducts() != null ? product.getStoreProducts() : Collections.emptyList();
        }
        List<StorePriceDTO> storeDtos = new ArrayList<>();

        Double minPrice = null;
        Double maxPrice = null;
        double sum = 0;
        int validStoreCount = 0;
        String bestStoreName = null;

        for (StoreProduct sp : storeProducts) {
            Double price = sp.getCurrentPrice();
            boolean hasValidPrice = price != null && price > 0 && !"UNAVAILABLE".equalsIgnoreCase(sp.getAvailability());

            if (hasValidPrice) {
                if (minPrice == null || price < minPrice) {
                    minPrice = price;
                    bestStoreName = sp.getStore();
                }
                if (maxPrice == null || price > maxPrice) {
                    maxPrice = price;
                }
                sum += price;
                validStoreCount++;
            }

            storeDtos.add(new StorePriceDTO(
                    sp.getId(),
                    sp.getStore(),
                    sp.getTitle(),
                    price,
                    sp.getCurrency(),
                    sp.getAvailability(),
                    sp.getProductUrl(),
                    sp.getImageUrl() != null ? sp.getImageUrl() : product.getImageUrl(),
                    sp.getStatus(),
                    sp.getLastCheckedAt(),
                    false
            ));
        }

        // Mark lowest store
        if (minPrice != null) {
            for (StorePriceDTO s : storeDtos) {
                if (s.getPrice() != null && Math.abs(s.getPrice() - minPrice) < 0.01) {
                    s.setLowest(true);
                }
            }
        }

        // Add unconfigured/unavailable store status entries dynamically based on ProviderManager
        boolean hasAmazon = storeDtos.stream().anyMatch(s -> "AMAZON".equalsIgnoreCase(s.getStore()));
        boolean hasFlipkart = storeDtos.stream().anyMatch(s -> "FLIPKART".equalsIgnoreCase(s.getStore()));
        boolean hasCroma = storeDtos.stream().anyMatch(s -> "CROMA".equalsIgnoreCase(s.getStore()));

        PriceProvider amz = (providerManager != null) ? providerManager.getProvider("AMAZON") : null;
        PriceProvider flp = (providerManager != null) ? providerManager.getProvider("FLIPKART") : null;
        PriceProvider crm = (providerManager != null) ? providerManager.getProvider("CROMA") : null;

        if (!hasAmazon) {
            boolean configured = amz != null && amz.isConfigured();
            String storeStatus = configured ? "UNAVAILABLE" : "CONFIG_REQUIRED";
            String title = configured ? "Amazon India" : "Amazon India (Config Required)";
            storeDtos.add(new StorePriceDTO(null, "AMAZON", title, null, "INR", "UNAVAILABLE", null, product.getImageUrl(), storeStatus, null, false));
        }
        if (!hasFlipkart) {
            boolean configured = flp != null && flp.isConfigured();
            String storeStatus = configured ? "UNAVAILABLE" : "CONFIG_REQUIRED";
            String title = configured ? "Flipkart" : "Flipkart (Affiliate API Unconfigured)";
            storeDtos.add(new StorePriceDTO(null, "FLIPKART", title, null, "INR", "UNAVAILABLE", null, product.getImageUrl(), storeStatus, null, false));
        }
        if (!hasCroma) {
            boolean configured = crm != null && crm.isConfigured();
            String storeStatus = configured ? "UNAVAILABLE" : "UNAVAILABLE";
            String title = "Croma (Public API Unavailable)";
            storeDtos.add(new StorePriceDTO(null, "CROMA", title, null, "INR", "UNAVAILABLE", null, product.getImageUrl(), storeStatus, null, false));
        }

        // Sort store listings: lowest price first, unavailable last
        storeDtos.sort(Comparator.comparing(
                (StorePriceDTO s) -> s.getPrice() == null ? Double.MAX_VALUE : s.getPrice()
        ));

        dto.setStores(storeDtos);
        dto.setLowestPrice(minPrice);
        dto.setHighestPrice(maxPrice);
        dto.setBestStore(bestStoreName);

        if (validStoreCount > 0) {
            double avg = Math.round((sum / validStoreCount) * 100.0) / 100.0;
            dto.setAveragePrice(avg);

            if (maxPrice != null && minPrice != null && maxPrice > minPrice) {
                double diff = Math.round((maxPrice - minPrice) * 100.0) / 100.0;
                double pct = Math.round(((maxPrice - minPrice) / maxPrice * 100.0) * 10.0) / 10.0;
                dto.setSavingsAmount(diff);
                dto.setSavingsPercentage(pct);
            } else {
                dto.setSavingsAmount(0.0);
                dto.setSavingsPercentage(0.0);
            }
        }

        // Intelligence summary and recommendation
        generateIntelligence(dto, product);

        return dto;
    }

    private void generateIntelligence(PriceComparisonDTO dto, Product product) {
        if (dto.getLowestPrice() == null) {
            dto.setTrendSummary("No live pricing available currently across connected providers.");
            dto.setRecommendation("INSUFFICIENT DATA");
            return;
        }

        if ("CATALOG".equalsIgnoreCase(dto.getBestStore())) {
            PriceProvider amz = (providerManager != null) ? providerManager.getProvider("AMAZON") : null;
            boolean amzConfigured = amz != null && amz.isConfigured();
            if (amzConfigured) {
                dto.setTrendSummary(String.format(
                        "Internal Catalog Benchmark: Baseline reference price ₹%,.0f. Amazon is connected via RapidAPI; tracking active for price updates.",
                        dto.getLowestPrice()
                ));
            } else {
                dto.setTrendSummary(String.format(
                        "Internal Catalog Benchmark: Baseline reference price ₹%,.0f. Connect Amazon RapidAPI or Flipkart Affiliate in .env to track real-time live store prices.",
                        dto.getLowestPrice()
                ));
            }
            dto.setRecommendation("CATALOG BENCHMARK");
            return;
        }

        List<PriceRecord> historical = priceRecordRepository.findByProductIdOrderByCheckedAtAsc(product.getId());

        if (historical.size() < 3) {
            if (dto.getSavingsAmount() != null && dto.getSavingsAmount() > 0) {
                dto.setTrendSummary(String.format(
                        "%s currently offers the lowest verified price at ₹%,.0f. You save ₹%,.0f (%.1f%%) compared to highest price.",
                        dto.getBestStore(), dto.getLowestPrice(), dto.getSavingsAmount(), dto.getSavingsPercentage()
                ));
                dto.setRecommendation("GOOD PRICE");
            } else {
                dto.setTrendSummary(String.format(
                        "Current lowest verified price is ₹%,.0f at %s. Price tracking is actively monitoring for future drops.",
                        dto.getLowestPrice(), dto.getBestStore()
                ));
                dto.setRecommendation("INSUFFICIENT DATA");
            }
            return;
        }

        // Calculate historical average
        double histSum = 0;
        double histMin = Double.MAX_VALUE;
        for (PriceRecord pr : historical) {
            histSum += pr.getPrice();
            if (pr.getPrice() < histMin) {
                histMin = pr.getPrice();
            }
        }
        double histAvg = histSum / historical.size();

        double diffFromAvg = Math.round(((dto.getLowestPrice() - histAvg) / histAvg * 100.0) * 10.0) / 10.0;

        if (dto.getLowestPrice() <= histMin) {
            dto.setTrendSummary(String.format(
                    "🔥 ALL-TIME LOW: Currently ₹%,.0f at %s, which is %.1f%% below the historical average of ₹%,.0f.",
                    dto.getLowestPrice(), dto.getBestStore(), Math.abs(diffFromAvg), histAvg
            ));
            dto.setRecommendation("BUY NOW");
        } else if (diffFromAvg < -3.0) {
            dto.setTrendSummary(String.format(
                    "Great deal: Current price is %.1f%% lower than the historical average (₹%,.0f). Best deal at %s.",
                    Math.abs(diffFromAvg), histAvg, dto.getBestStore()
            ));
            dto.setRecommendation("BUY NOW");
        } else if (diffFromAvg > 5.0) {
            dto.setTrendSummary(String.format(
                    "Price is elevated: Currently %.1f%% higher than historical average of ₹%,.0f. Consider setting a price drop alert.",
                    diffFromAvg, histAvg
            ));
            dto.setRecommendation("WAIT FOR LOWER PRICE");
        } else {
            dto.setTrendSummary(String.format(
                    "Price is stable: Close to historical average of ₹%,.0f. Best available at %s.",
                    histAvg, dto.getBestStore()
            ));
            dto.setRecommendation("GOOD PRICE");
        }
    }
}
