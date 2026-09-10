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

        List<StoreProduct> rawStoreProducts = null;
        if (storeProductRepository != null && product.getId() != null) {
            rawStoreProducts = storeProductRepository.findByProductId(product.getId());
        }
        if (rawStoreProducts == null || rawStoreProducts.isEmpty()) {
            rawStoreProducts = product.getStoreProducts() != null ? product.getStoreProducts() : Collections.emptyList();
        }

        // Only allow real active stores: AMAZON and FLIPKART
        List<StoreProduct> storeProducts = (rawStoreProducts != null)
                ? rawStoreProducts.stream()
                    .filter(sp -> sp != null && ("AMAZON".equalsIgnoreCase(sp.getStore()) || "FLIPKART".equalsIgnoreCase(sp.getStore())))
                    .toList()
                : Collections.emptyList();

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
        boolean hasAmazon = storeDtos.stream().anyMatch(s -> "AMAZON".equalsIgnoreCase(s.getStore()) && s.getPrice() != null && s.getPrice() > 0);
        boolean hasFlipkart = storeDtos.stream().anyMatch(s -> "FLIPKART".equalsIgnoreCase(s.getStore()) && s.getPrice() != null && s.getPrice() > 0);

        PriceProvider amz = (providerManager != null) ? providerManager.getProvider("AMAZON") : null;
        PriceProvider flp = (providerManager != null) ? providerManager.getProvider("FLIPKART") : null;

        // If product has Amazon but lacks Flipkart, attempt live on-demand query if Flipkart is configured
        if (!hasFlipkart && flp != null && flp.isConfigured() && product.getId() != null) {
            try {
                String searchTarget = cleanSearchTarget(product);
                if (searchTarget != null && !searchTarget.trim().isEmpty()) {
                    List<com.pricewise.backend.dto.ProviderProductDTO> items = flp.searchProducts(searchTarget);
                    if (items != null && !items.isEmpty()) {
                        com.pricewise.backend.dto.ProviderProductDTO item = items.stream()
                                .filter(candidate -> isCompatibleItem(product, candidate) && candidate.getPrice() != null && candidate.getPrice() > 0)
                                .findFirst()
                                .orElse(null);

                        if (item != null) {
                            StoreProduct sp = new StoreProduct(
                                    product,
                                    "FLIPKART",
                                    item.getStoreProductId(),
                                    item.getTitle(),
                                    item.getProductUrl(),
                                    item.getPrice(),
                                    item.getCurrency(),
                                    item.getAvailability(),
                                    item.getStatus()
                            );
                            sp.setImageUrl(item.getImageUrl() != null ? item.getImageUrl() : product.getImageUrl());
                            sp.setLastCheckedAt(java.time.LocalDateTime.now());
                            if (storeProductRepository != null) {
                                sp = storeProductRepository.save(sp);
                            }
                            if (priceRecordRepository != null) {
                                PriceRecord rec = new PriceRecord(sp, sp.getCurrentPrice(), sp.getCurrency(), sp.getAvailability(), sp.getStatus());
                                priceRecordRepository.save(rec);
                            }

                            Double price = sp.getCurrentPrice();
                            if (minPrice == null || price < minPrice) {
                                minPrice = price;
                                bestStoreName = "FLIPKART";
                            }
                            if (maxPrice == null || price > maxPrice) {
                                maxPrice = price;
                            }
                            sum += price;
                            validStoreCount++;

                            storeDtos.add(new StorePriceDTO(
                                    sp.getId(),
                                    sp.getStore(),
                                    sp.getTitle(),
                                    price,
                                    sp.getCurrency(),
                                    sp.getAvailability(),
                                    sp.getProductUrl(),
                                    sp.getImageUrl(),
                                    sp.getStatus(),
                                    sp.getLastCheckedAt(),
                                    false
                            ));
                            hasFlipkart = true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // If product has Flipkart but lacks Amazon, attempt live on-demand query if Amazon is configured
        if (!hasAmazon && amz != null && amz.isConfigured() && product.getId() != null) {
            try {
                String searchTarget = cleanSearchTarget(product);
                if (searchTarget != null && !searchTarget.trim().isEmpty()) {
                    List<com.pricewise.backend.dto.ProviderProductDTO> items = amz.searchProducts(searchTarget);
                    if (items != null && !items.isEmpty()) {
                        com.pricewise.backend.dto.ProviderProductDTO item = items.stream()
                                .filter(candidate -> isCompatibleItem(product, candidate) && candidate.getPrice() != null && candidate.getPrice() > 0)
                                .findFirst()
                                .orElse(null);

                        if (item != null) {
                            StoreProduct sp = new StoreProduct(
                                    product,
                                    "AMAZON",
                                    item.getStoreProductId(),
                                    item.getTitle(),
                                    item.getProductUrl(),
                                    item.getPrice(),
                                    item.getCurrency(),
                                    item.getAvailability(),
                                    item.getStatus()
                            );
                            sp.setImageUrl(item.getImageUrl() != null ? item.getImageUrl() : product.getImageUrl());
                            sp.setLastCheckedAt(java.time.LocalDateTime.now());
                            if (storeProductRepository != null) {
                                sp = storeProductRepository.save(sp);
                            }
                            if (priceRecordRepository != null) {
                                PriceRecord rec = new PriceRecord(sp, sp.getCurrentPrice(), sp.getCurrency(), sp.getAvailability(), sp.getStatus());
                                priceRecordRepository.save(rec);
                            }

                            Double price = sp.getCurrentPrice();
                            if (minPrice == null || price < minPrice) {
                                minPrice = price;
                                bestStoreName = "AMAZON";
                            }
                            if (maxPrice == null || price > maxPrice) {
                                maxPrice = price;
                            }
                            sum += price;
                            validStoreCount++;

                            storeDtos.add(new StorePriceDTO(
                                    sp.getId(),
                                    sp.getStore(),
                                    sp.getTitle(),
                                    price,
                                    sp.getCurrency(),
                                    sp.getAvailability(),
                                    sp.getProductUrl(),
                                    sp.getImageUrl(),
                                    sp.getStatus(),
                                    sp.getLastCheckedAt(),
                                    false
                            ));
                            hasAmazon = true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Add honest unavailable / config required entries only if genuine offer not found
        if (!hasAmazon) {
            boolean configured = amz != null && amz.isConfigured();
            String storeStatus = configured ? "UNAVAILABLE" : "CONFIG_REQUIRED";
            String title = configured ? "Amazon (No matching result)" : "Amazon (API Key Required)";
            storeDtos.add(new StorePriceDTO(null, "AMAZON", title, null, "INR", "UNAVAILABLE", null, product.getImageUrl(), storeStatus, null, false));
        }
        if (!hasFlipkart) {
            boolean configured = flp != null && flp.isConfigured();
            String storeStatus = configured ? "UNAVAILABLE" : "CONFIG_REQUIRED";
            String title = configured ? "Flipkart (No matching result)" : "Flipkart (API Key Required)";
            storeDtos.add(new StorePriceDTO(null, "FLIPKART", title, null, "INR", "UNAVAILABLE", null, product.getImageUrl(), storeStatus, null, false));
        }

        // Re-evaluate lowest price flag across all stores
        if (minPrice != null) {
            for (StorePriceDTO s : storeDtos) {
                s.setLowest(s.getPrice() != null && Math.abs(s.getPrice() - minPrice) < 0.01);
            }
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

            // Only show savings amount when both valid prices are available
            if (validStoreCount >= 2 && maxPrice != null && minPrice != null && maxPrice > minPrice) {
                double diff = Math.round((maxPrice - minPrice) * 100.0) / 100.0;
                double pct = Math.round(((maxPrice - minPrice) / maxPrice * 100.0) * 10.0) / 10.0;
                dto.setSavingsAmount(diff);
                dto.setSavingsPercentage(pct);
            } else {
                dto.setSavingsAmount(null);
                dto.setSavingsPercentage(null);
            }
        }

        // Intelligence summary and recommendation
        generateIntelligence(dto, product);

        return dto;
    }

    private void generateIntelligence(PriceComparisonDTO dto, Product product) {
        if (dto.getLowestPrice() == null) {
            dto.setTrendSummary("No live pricing available currently across Amazon and Flipkart.");
            dto.setRecommendation("INSUFFICIENT DATA");
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

    private static final java.util.Set<String> MODEL_VARIANTS = java.util.Set.of("pro", "plus", "ultra", "mini", "max", "air", "fe", "lite", "neo");

    public static String cleanSearchTarget(Product product) {
        if (product == null) return "";
        String canonical = product.getCanonicalName() != null ? product.getCanonicalName() : product.getProductName();
        if (canonical == null || canonical.trim().isEmpty()) return "";

        String s = canonical.trim();
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren);
        int bracket = s.indexOf('[');
        if (bracket > 0) s = s.substring(0, bracket);
        int pipe = s.indexOf('|');
        if (pipe > 0) s = s.substring(0, pipe);
        int dash = s.indexOf(" - ");
        if (dash > 0) s = s.substring(0, dash);

        s = s.replaceAll("(?i)\\b(5g|4g|lte|smartphone|phone|mobile|storage|ram|rom)\\b", "").trim().replaceAll("\\s+", " ");

        if (product.getBrand() != null && !product.getBrand().trim().isEmpty()) {
            String b = product.getBrand().trim();
            if (!s.toLowerCase().contains(b.toLowerCase())) {
                s = b + " " + s;
            }
        }
        return s.trim();
    }

    private boolean isCompatibleItem(Product product, com.pricewise.backend.dto.ProviderProductDTO item) {
        if (product == null || item == null || item.getTitle() == null) return false;
        String pTitle = (product.getCanonicalName() != null ? product.getCanonicalName() : product.getProductName()).toLowerCase();
        String iTitle = item.getTitle().toLowerCase();

        for (String v : MODEL_VARIANTS) {
            boolean pHas = pTitle.contains(" " + v) || pTitle.contains("-" + v) || pTitle.endsWith(" " + v);
            boolean iHas = iTitle.contains(" " + v) || iTitle.contains("-" + v) || iTitle.endsWith(" " + v);
            if (pHas != iHas) {
                return false;
            }
        }
        return true;
    }
}
