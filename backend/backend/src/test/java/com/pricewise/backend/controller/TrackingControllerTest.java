package com.pricewise.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.TrackedProductDTO;
import com.pricewise.backend.dto.TrackingRequestDTO;
import com.pricewise.backend.service.FirebaseAuthService;
import com.pricewise.backend.service.PriceTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrackingControllerTest {

    private MockMvc mockMvc;
    private PriceTrackingService priceTrackingService;
    private FirebaseAuthService firebaseAuthService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        priceTrackingService = Mockito.mock(PriceTrackingService.class);
        // FirebaseAuthService with null FirebaseApp supports test-token- prefixes
        firebaseAuthService = new FirebaseAuthService(null);
        TrackingController controller = new TrackingController(priceTrackingService, firebaseAuthService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetTrackedProducts_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/tracking"))
                .andExpect(status().isUnauthorized());

        verify(priceTrackingService, never()).getTrackedProducts(anyString());
    }

    @Test
    void testGetTrackedProducts_Authenticated_Returns200WithUserScopedItems() throws Exception {
        TrackedProductDTO item = new TrackedProductDTO();
        item.setId(1L);
        item.setStore("AMAZON");
        when(priceTrackingService.getTrackedProducts("user-123")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/tracking")
                        .header("Authorization", "Bearer test-token-user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].store").value("AMAZON"));

        verify(priceTrackingService).getTrackedProducts("user-123");
    }

    @Test
    void testTrackProduct_Unauthenticated_Returns401() throws Exception {
        TrackingRequestDTO req = new TrackingRequestDTO();
        req.setStoreProductId(10L);

        mockMvc.perform(post("/api/tracking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(priceTrackingService, never()).trackProduct(any(), any());
    }

    @Test
    void testTrackProduct_Authenticated_PassesVerifiedUid() throws Exception {
        TrackingRequestDTO req = new TrackingRequestDTO();
        req.setStoreProductId(10L);
        req.setTargetPrice(45000.0);

        TrackedProductDTO created = new TrackedProductDTO();
        created.setId(99L);
        created.setTargetPrice(45000.0);

        when(priceTrackingService.trackProduct(any(TrackingRequestDTO.class), eq("user-456"))).thenReturn(created);

        mockMvc.perform(post("/api/tracking")
                        .header("Authorization", "Bearer test-token-user-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99L));

        verify(priceTrackingService).trackProduct(any(TrackingRequestDTO.class), eq("user-456"));
    }

    @Test
    void testStopTracking_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(delete("/api/tracking/42"))
                .andExpect(status().isUnauthorized());

        verify(priceTrackingService, never()).stopTracking(anyLong(), anyString());
    }

    @Test
    void testStopTracking_Authenticated_Succeeds() throws Exception {
        doNothing().when(priceTrackingService).stopTracking(42L, "user-789");

        mockMvc.perform(delete("/api/tracking/42")
                        .header("Authorization", "Bearer test-token-user-789"))
                .andExpect(status().isNoContent());

        verify(priceTrackingService).stopTracking(42L, "user-789");
    }

    @Test
    void testCheckPriceNow_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/tracking/42/check-now"))
                .andExpect(status().isUnauthorized());

        verify(priceTrackingService, never()).checkPriceNow(anyLong(), anyString());
    }

    @Test
    void testCheckPriceNow_Authenticated_CallsServiceWithUid() throws Exception {
        TrackedProductDTO updated = new TrackedProductDTO();
        updated.setId(42L);

        when(priceTrackingService.checkPriceNow(42L, "user-789")).thenReturn(updated);

        mockMvc.perform(post("/api/tracking/42/check-now")
                        .header("Authorization", "Bearer test-token-user-789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42L));

        verify(priceTrackingService).checkPriceNow(42L, "user-789");
    }
}
