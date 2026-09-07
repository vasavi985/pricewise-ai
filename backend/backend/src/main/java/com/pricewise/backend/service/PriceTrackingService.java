package com.pricewise.backend.service;

import com.pricewise.backend.dto.TrackedProductDTO;
import com.pricewise.backend.dto.TrackingRequestDTO;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.notification.NotificationService;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import com.pricewise.backend.repository.TrackedProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PriceTrackingService {

    private static final Logger log = LoggerFactory.getLogger(PriceTrackingService.class);

    private final TrackedProductRepository trackedProductRepository;
    private final StoreProductRepository storeProductRepository;
    private final PriceRecordRepository priceRecordRepository;
    private final NotificationService notificationService;

    public PriceTrackingService(TrackedProductRepository trackedProductRepository,
                                StoreProductRepository storeProductRepository,
                                PriceRecordRepository priceRecordRepository,
                                NotificationService notificationService) {
        this.trackedProductRepository = trackedProductRepository;
        this.storeProductRepository = storeProductRepository;
        this.priceRecordRepository = priceRecordRepository;
        this.notificationService = notificationService;
    }

    public TrackedProductDTO trackProduct(TrackingRequestDTO req) {
        StoreProduct sp = storeProductRepository.findById(req.getStoreProductId())
                .orElseThrow(() -> new RuntimeException("Store product not found: " + req.getStoreProductId()));

        String userId = (req.getUserId() != null && !req.getUserId().trim().isEmpty()) ? req.getUserId() : "local-user";

        Optional<TrackedProduct> existing = trackedProductRepository.findByStoreProductIdAndUserIdAndActiveTrue(sp.getId(), userId);
        TrackedProduct tracked;

        if (existing.isPresent()) {
            tracked = existing.get();
            if (req.getUserEmail() != null) tracked.setUserEmail(req.getUserEmail());
            if (req.getTargetPrice() != null) tracked.setTargetPrice(req.getTargetPrice());
            if (req.getTargetDropPercentage() != null) tracked.setTargetDropPercentage(req.getTargetDropPercentage());
        } else {
            tracked = new TrackedProduct();
            tracked.setStoreProduct(sp);
            tracked.setStoreProductId(sp.getId());
            tracked.setUserId(userId);
            tracked.setUserEmail(req.getUserEmail());
            tracked.setInitialPrice(sp.getCurrentPrice());
            tracked.setTargetPrice(req.getTargetPrice() != null ? req.getTargetPrice() : (sp.getCurrentPrice() != null ? Math.round(sp.getCurrentPrice() * 0.95) : null));
            tracked.setTargetDropPercentage(req.getTargetDropPercentage() != null ? req.getTargetDropPercentage() : 5.0);
            tracked.setActive(true);
        }

        tracked = trackedProductRepository.save(tracked);
        String prodName = (sp.getProduct() != null) ? sp.getProduct().getCanonicalName() : sp.getTitle();
        log.info("User [{}] started tracking [{}] at {} (Target: ₹{})", userId, prodName, sp.getStore(), tracked.getTargetPrice());

        return toDto(tracked);
    }

    public List<TrackedProductDTO> getTrackedProducts(String userId) {
        String uid = (userId != null && !userId.trim().isEmpty()) ? userId : "local-user";
        List<TrackedProduct> list = trackedProductRepository.findByUserIdAndActiveTrue(uid);
        List<TrackedProductDTO> dtos = new ArrayList<>();
        for (TrackedProduct tp : list) {
            dtos.add(toDto(tp));
        }
        return dtos;
    }

    public TrackedProductDTO getTrackedProduct(Long id) {
        TrackedProduct tp = trackedProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tracked product not found: " + id));
        return toDto(tp);
    }

    public void stopTracking(Long id) {
        TrackedProduct tp = trackedProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tracked product not found: " + id));
        tp.setActive(false);
        trackedProductRepository.save(tp);
        log.info("Stopped tracking item ID: {}", id);
    }

    public TrackedProductDTO checkPriceNow(Long id) {
        TrackedProduct tp = trackedProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tracked product not found: " + id));

        tp.setLastCheckedAt(LocalDateTime.now());
        trackedProductRepository.save(tp);

        return toDto(tp);
    }

    public void evaluatePriceChange(TrackedProduct tp, double previousPrice, double newPrice) {
        if (newPrice < previousPrice) {
            double dropAmount = Math.round((previousPrice - newPrice) * 100.0) / 100.0;
            double dropPercentage = Math.round(((previousPrice - newPrice) / previousPrice * 100.0) * 10.0) / 10.0;

            boolean qualifiesByTargetPrice = (tp.getTargetPrice() != null && newPrice <= tp.getTargetPrice());
            boolean qualifiesByPercentage = (tp.getTargetDropPercentage() != null && dropPercentage >= tp.getTargetDropPercentage());

            if (qualifiesByTargetPrice || qualifiesByPercentage) {
                // Avoid notifying repeatedly for the same dropped price
                if (tp.getLastNotifiedPrice() == null || newPrice < tp.getLastNotifiedPrice()) {
                    notificationService.sendPriceDropAlert(tp, previousPrice, newPrice, dropAmount, dropPercentage);
                    tp.setLastNotifiedPrice(newPrice);
                    trackedProductRepository.save(tp);
                }
            }
        }
    }

    private TrackedProductDTO toDto(TrackedProduct tp) {
        TrackedProductDTO dto = new TrackedProductDTO();
        dto.setId(tp.getId());
        dto.setStoreProductId(tp.getStoreProductId());

        StoreProduct sp = tp.getStoreProduct();
        if (sp != null) {
            dto.setStore(sp.getStore());
            dto.setProductUrl(sp.getProductUrl());
            dto.setCurrentPrice(sp.getCurrentPrice());

            if (sp.getProduct() != null) {
                dto.setProductId(sp.getProduct().getId());
                dto.setProductName(sp.getProduct().getCanonicalName());
                dto.setImageUrl(sp.getImageUrl() != null ? sp.getImageUrl() : sp.getProduct().getImageUrl());
            } else {
                dto.setProductId(sp.getProductId());
                dto.setProductName(sp.getTitle());
                dto.setImageUrl(sp.getImageUrl());
            }
        }

        dto.setInitialPrice(tp.getInitialPrice());
        dto.setTargetPrice(tp.getTargetPrice());
        dto.setTargetDropPercentage(tp.getTargetDropPercentage());
        dto.setLastNotifiedPrice(tp.getLastNotifiedPrice());
        dto.setLastCheckedAt(tp.getLastCheckedAt());
        dto.setCreatedAt(tp.getCreatedAt());
        dto.setActive(tp.getActive());
        dto.setUserEmail(tp.getUserEmail());

        Double currentPrice = (sp != null) ? sp.getCurrentPrice() : null;
        if (tp.getInitialPrice() != null && currentPrice != null) {
            double diff = tp.getInitialPrice() - currentPrice;
            dto.setPriceDrop(Math.round(diff * 100.0) / 100.0);
            if (tp.getInitialPrice() > 0) {
                dto.setDropPercentage(Math.round((diff / tp.getInitialPrice() * 100.0) * 10.0) / 10.0);
            }
        }

        return dto;
    }
}
