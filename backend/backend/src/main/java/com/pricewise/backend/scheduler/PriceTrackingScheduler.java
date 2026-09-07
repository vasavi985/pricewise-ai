package com.pricewise.backend.scheduler;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.entity.PriceRecord;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.provider.PriceProvider;
import com.pricewise.backend.provider.ProviderManager;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import com.pricewise.backend.repository.TrackedProductRepository;
import com.pricewise.backend.service.PriceTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PriceTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceTrackingScheduler.class);

    private final TrackedProductRepository trackedProductRepository;
    private final StoreProductRepository storeProductRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final ProviderManager providerManager;
    private final PriceTrackingService priceTrackingService;

    @Value("${pricewise.tracking.enabled:true}")
    private boolean trackingEnabled;

    public PriceTrackingScheduler(TrackedProductRepository trackedProductRepository,
                                  StoreProductRepository storeProductRepository,
                                  PriceRecordRepository priceRecordRepository,
                                  ProviderManager providerManager,
                                  PriceTrackingService priceTrackingService) {
        this.trackedProductRepository = trackedProductRepository;
        this.storeProductRepository = storeProductRepository;
        this.priceRecordRepository = priceRecordRepository;
        this.providerManager = providerManager;
        this.priceTrackingService = priceTrackingService;
    }

    @Scheduled(fixedDelayString = "${pricewise.tracking.interval:60000}", initialDelay = 15000)
    public void runPriceCheckJob() {
        if (!trackingEnabled) {
            log.debug("Price tracking scheduler is disabled by configuration.");
            return;
        }

        List<TrackedProduct> activeTracked = trackedProductRepository.findByActiveTrue();
        if (activeTracked.isEmpty()) {
            log.debug("No active tracked products found. Scheduler cycle complete.");
            return;
        }

        log.info("Starting automated price tracking cycle for {} active tracked products...", activeTracked.size());

        for (TrackedProduct tp : activeTracked) {
            try {
                checkPriceForTrackedItem(tp);
            } catch (Exception e) {
                log.error("Failed automated price check for tracked item ID {}: {}", tp.getId(), e.getMessage());
            }
        }

        log.info("Completed automated price tracking cycle.");
    }

    private void checkPriceForTrackedItem(TrackedProduct tp) {
        StoreProduct sp = tp.getStoreProduct();
        if (sp == null && tp.getStoreProductId() != null) {
            sp = storeProductRepository.findById(tp.getStoreProductId()).orElse(null);
        }
        if (sp == null) {
            log.warn("Cannot check price for tracked item ID {}: StoreProduct not found.", tp.getId());
            return;
        }

        String store = sp.getStore();
        Double oldPrice = sp.getCurrentPrice();
        String prodName = (sp.getProduct() != null) ? sp.getProduct().getCanonicalName() : sp.getTitle();

        PriceProvider provider = providerManager.getProvider(store);
        if (provider != null && provider.isConfigured()) {
            ProviderProductDTO fresh = provider.fetchCurrentPrice(sp.getStoreProductId(), sp.getProductUrl());
            if (fresh != null && fresh.getPrice() != null) {
                Double newPrice = fresh.getPrice();
                sp.setAvailability(fresh.getAvailability());
                sp.setStatus(fresh.getStatus());
                sp.setLastCheckedAt(LocalDateTime.now());

                if (oldPrice != null && Math.abs(oldPrice - newPrice) > 0.01) {
                    log.info("Price change detected by scheduler for [{}] ({}) -> was ₹{}, now ₹{}",
                            prodName, store, oldPrice, newPrice);
                    sp.setCurrentPrice(newPrice);
                    storeProductRepository.save(sp);

                    // Append immutable price record with explicit status
                    PriceRecord record = new PriceRecord(sp, newPrice, sp.getCurrency(), sp.getAvailability(), fresh.getStatus(), "SCHEDULER");
                    priceRecordRepository.save(record);

                    // Evaluate potential price drop notifications
                    priceTrackingService.evaluatePriceChange(tp, oldPrice, newPrice);
                } else {
                    storeProductRepository.save(sp);
                }
            } else {
                log.warn("Scheduled price fetch failed or returned no valid price for [{}] at {}. Status updated to FETCH_FAILED without creating misleading price record.",
                        prodName, store);
                sp.setStatus("FETCH_FAILED");
                sp.setLastCheckedAt(LocalDateTime.now());
                storeProductRepository.save(sp);
            }
        } else {
            log.debug("Skipping scheduler check for store {}: Provider is not configured or unavailable.", store);
        }

        tp.setLastCheckedAt(LocalDateTime.now());
        trackedProductRepository.save(tp);
    }
}
