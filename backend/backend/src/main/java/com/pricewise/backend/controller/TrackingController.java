package com.pricewise.backend.controller;

import com.pricewise.backend.dto.TrackedProductDTO;
import com.pricewise.backend.dto.TrackingRequestDTO;
import com.pricewise.backend.service.PriceTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final PriceTrackingService priceTrackingService;

    public TrackingController(PriceTrackingService priceTrackingService) {
        this.priceTrackingService = priceTrackingService;
    }

    @PostMapping
    public ResponseEntity<TrackedProductDTO> trackProduct(@Valid @RequestBody TrackingRequestDTO request) {
        TrackedProductDTO tracked = priceTrackingService.trackProduct(request);
        return ResponseEntity.ok(tracked);
    }

    @GetMapping
    public ResponseEntity<List<TrackedProductDTO>> getTrackedProducts(@RequestParam(value = "userId", required = false, defaultValue = "local-user") String userId) {
        List<TrackedProductDTO> list = priceTrackingService.getTrackedProducts(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackedProductDTO> getTrackedProduct(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(priceTrackingService.getTrackedProduct(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stopTracking(@PathVariable Long id) {
        try {
            priceTrackingService.stopTracking(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/check-now")
    public ResponseEntity<TrackedProductDTO> checkPriceNow(@PathVariable Long id) {
        try {
            TrackedProductDTO updated = priceTrackingService.checkPriceNow(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
