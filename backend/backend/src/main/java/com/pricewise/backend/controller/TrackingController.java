package com.pricewise.backend.controller;

import com.pricewise.backend.dto.TrackedProductDTO;
import com.pricewise.backend.dto.TrackingRequestDTO;
import com.pricewise.backend.service.FirebaseAuthService;
import com.pricewise.backend.service.PriceTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final PriceTrackingService priceTrackingService;
    private final FirebaseAuthService firebaseAuthService;

    public TrackingController(PriceTrackingService priceTrackingService,
                              FirebaseAuthService firebaseAuthService) {
        this.priceTrackingService = priceTrackingService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping
    public ResponseEntity<TrackedProductDTO> trackProduct(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody TrackingRequestDTO request) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        TrackedProductDTO tracked = priceTrackingService.trackProduct(request, uid);
        return ResponseEntity.ok(tracked);
    }

    @GetMapping
    public ResponseEntity<List<TrackedProductDTO>> getTrackedProducts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        List<TrackedProductDTO> list = priceTrackingService.getTrackedProducts(uid);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackedProductDTO> getTrackedProduct(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        return ResponseEntity.ok(priceTrackingService.getTrackedProduct(id, uid));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stopTracking(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        priceTrackingService.stopTracking(id, uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check-now")
    public ResponseEntity<TrackedProductDTO> checkPriceNow(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        TrackedProductDTO updated = priceTrackingService.checkPriceNow(id, uid);
        return ResponseEntity.ok(updated);
    }
}
