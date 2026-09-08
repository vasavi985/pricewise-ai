package com.pricewise.backend.service;

import com.pricewise.backend.dto.TrackedProductDTO;
import com.pricewise.backend.dto.TrackingRequestDTO;
import com.pricewise.backend.entity.Product;
import com.pricewise.backend.entity.StoreProduct;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.notification.NotificationService;
import com.pricewise.backend.repository.PriceRecordRepository;
import com.pricewise.backend.repository.StoreProductRepository;
import com.pricewise.backend.repository.TrackedProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PriceTrackingServiceTest {

    private TrackedProductRepository trackedProductRepository;
    private StoreProductRepository storeProductRepository;
    private PriceRecordRepository priceRecordRepository;
    private NotificationService notificationService;
    private PriceTrackingService priceTrackingService;

    @BeforeEach
    void setUp() {
        trackedProductRepository = Mockito.mock(TrackedProductRepository.class);
        storeProductRepository = Mockito.mock(StoreProductRepository.class);
        priceRecordRepository = Mockito.mock(PriceRecordRepository.class);
        notificationService = Mockito.mock(NotificationService.class);

        priceTrackingService = new PriceTrackingService(
                trackedProductRepository,
                storeProductRepository,
                priceRecordRepository,
                notificationService
        );
    }

    @Test
    void testPriceDropBelowTarget_TriggersNotification() {
        Product p = new Product();
        p.setCanonicalName("Test Phone");

        StoreProduct sp = new StoreProduct();
        sp.setProduct(p);
        sp.setStore("AMAZON");
        sp.setProductUrl("http://example.com");

        TrackedProduct tp = new TrackedProduct();
        tp.setId(1L);
        tp.setStoreProduct(sp);
        tp.setTargetPrice(55000.0);
        tp.setTargetDropPercentage(10.0);
        tp.setLastNotifiedPrice(null);

        // Previous: 56000, New: 54999 (New price is below target 55000)
        priceTrackingService.evaluatePriceChange(tp, 56000.0, 54999.0);

        verify(notificationService, times(1)).sendPriceDropAlert(
                eq(tp), eq(56000.0), eq(54999.0), eq(1001.0), anyDouble()
        );
        verify(trackedProductRepository, times(1)).save(tp);
    }

    @Test
    void testPriceDropAboveTarget_DoesNotTriggerNotification() {
        Product p = new Product();
        p.setCanonicalName("Test Phone");

        StoreProduct sp = new StoreProduct();
        sp.setProduct(p);
        sp.setStore("AMAZON");

        TrackedProduct tp = new TrackedProduct();
        tp.setId(2L);
        tp.setStoreProduct(sp);
        tp.setTargetPrice(55000.0);
        tp.setTargetDropPercentage(10.0); // 10% of 57000 is 5700, actual drop is 1000 (1.75%)
        tp.setLastNotifiedPrice(null);

        // Previous: 57000, New: 56000 (Above target 55000 and below 10% threshold)
        priceTrackingService.evaluatePriceChange(tp, 57000.0, 56000.0);

        verify(notificationService, never()).sendPriceDropAlert(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(trackedProductRepository, never()).save(any());
    }

    @Test
    void testDuplicateNotification_PreventedForSamePrice() {
        Product p = new Product();
        p.setCanonicalName("Test Phone");

        StoreProduct sp = new StoreProduct();
        sp.setProduct(p);
        sp.setStore("FLIPKART");

        TrackedProduct tp = new TrackedProduct();
        tp.setId(3L);
        tp.setStoreProduct(sp);
        tp.setTargetPrice(55000.0);
        tp.setTargetDropPercentage(5.0);
        tp.setLastNotifiedPrice(54999.0); // Already notified at 54999

        // Another check with same price 54999
        priceTrackingService.evaluatePriceChange(tp, 56000.0, 54999.0);

        verify(notificationService, never()).sendPriceDropAlert(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void testTrackProduct_UsesAuthenticatedUid() {
        StoreProduct sp = new StoreProduct();
        sp.setId(10L);
        sp.setCurrentPrice(50000.0);
        when(storeProductRepository.findById(10L)).thenReturn(Optional.of(sp));
        when(trackedProductRepository.findByStoreProductIdAndUserIdAndActiveTrue(10L, "auth-user-999"))
                .thenReturn(Optional.empty());

        TrackedProduct saved = new TrackedProduct();
        saved.setId(1L);
        saved.setUserId("auth-user-999");
        saved.setStoreProduct(sp);
        when(trackedProductRepository.save(any(TrackedProduct.class))).thenReturn(saved);

        TrackingRequestDTO req = new TrackingRequestDTO();
        req.setStoreProductId(10L);
        req.setUserId("spoofed-attacker-id"); // Client attempts to spoof another user ID

        TrackedProductDTO dto = priceTrackingService.trackProduct(req, "auth-user-999");

        assertNotNull(dto);
        // Verify repository saved entity strictly with verified UID auth-user-999
        verify(trackedProductRepository).save(argThat(tp -> "auth-user-999".equals(tp.getUserId())));
    }

    @Test
    void testStopTracking_DifferentUser_ThrowsResponseStatusException() {
        TrackedProduct tp = new TrackedProduct();
        tp.setId(5L);
        tp.setUserId("victim-user");
        tp.setActive(true);

        when(trackedProductRepository.findById(5L)).thenReturn(Optional.of(tp));

        // Attacker attempts to stop tracking victim's item
        assertThrows(ResponseStatusException.class, () -> {
            priceTrackingService.stopTracking(5L, "attacker-user");
        });

        verify(trackedProductRepository, never()).save(any());
    }

    @Test
    void testStopTracking_SameUser_Succeeds() {
        TrackedProduct tp = new TrackedProduct();
        tp.setId(5L);
        tp.setUserId("legitimate-user");
        tp.setActive(true);

        when(trackedProductRepository.findById(5L)).thenReturn(Optional.of(tp));

        priceTrackingService.stopTracking(5L, "legitimate-user");

        assertFalse(tp.getActive());
        verify(trackedProductRepository, times(1)).save(tp);
    }

    @Test
    void testGetTrackedProduct_DifferentUser_ThrowsResponseStatusException() {
        TrackedProduct tp = new TrackedProduct();
        tp.setId(7L);
        tp.setUserId("owner-user");

        when(trackedProductRepository.findById(7L)).thenReturn(Optional.of(tp));

        assertThrows(ResponseStatusException.class, () -> {
            priceTrackingService.getTrackedProduct(7L, "intruder-user");
        });
    }

    @Test
    void testCheckPriceNow_DifferentUser_ThrowsResponseStatusException() {
        TrackedProduct tp = new TrackedProduct();
        tp.setId(8L);
        tp.setUserId("owner-user");

        when(trackedProductRepository.findById(8L)).thenReturn(Optional.of(tp));

        assertThrows(ResponseStatusException.class, () -> {
            priceTrackingService.checkPriceNow(8L, "intruder-user");
        });
    }
}
