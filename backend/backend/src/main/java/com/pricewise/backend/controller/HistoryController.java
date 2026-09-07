package com.pricewise.backend.controller;

import com.pricewise.backend.dto.UserSearchHistoryDTO;
import com.pricewise.backend.entity.UserSearchHistory;
import com.pricewise.backend.repository.UserSearchHistoryRepository;
import com.pricewise.backend.service.FirebaseAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final UserSearchHistoryRepository historyRepository;
    private final FirebaseAuthService firebaseAuthService;

    public HistoryController(UserSearchHistoryRepository historyRepository,
                             FirebaseAuthService firebaseAuthService) {
        this.historyRepository = historyRepository;
        this.firebaseAuthService = firebaseAuthService;
    }

    /**
     * Returns search history exclusively for the authenticated Firebase user.
     * Rejects request if unauthenticated.
     */
    @GetMapping
    public ResponseEntity<List<UserSearchHistoryDTO>> getUserHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);

        List<UserSearchHistory> records = historyRepository.findByUserId(uid);
        List<UserSearchHistoryDTO> dtoList = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    /**
     * Manually records a search entry for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<UserSearchHistoryDTO> recordSearch(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody UserSearchHistoryDTO request) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);

        UserSearchHistory history = new UserSearchHistory();
        history.setUserId(uid); // Source of truth from verified token!
        history.setQuery(request.getQuery() != null ? request.getQuery().trim() : "");
        history.setResultCount(request.getResultCount() != null ? request.getResultCount() : 0);
        history.setTopProductName(request.getTopProductName());
        history.setTopProductPrice(request.getTopProductPrice());
        history.setTopProductStore(request.getTopProductStore());
        history.setTopProductImage(request.getTopProductImage());
        history.setTopProductId(request.getTopProductId());

        UserSearchHistory saved = historyRepository.save(history);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }

    /**
     * Deletes ALL history records for ONLY the authenticated user.
     */
    @DeleteMapping
    public ResponseEntity<Void> clearUserHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String uid = firebaseAuthService.verifyTokenAndGetUid(authHeader);
        historyRepository.deleteAllByUserId(uid);
        return ResponseEntity.noContent().build();
    }

    private UserSearchHistoryDTO toDTO(UserSearchHistory entity) {
        UserSearchHistoryDTO dto = new UserSearchHistoryDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setQuery(entity.getQuery());
        dto.setResultCount(entity.getResultCount());
        dto.setTopProductName(entity.getTopProductName());
        dto.setTopProductPrice(entity.getTopProductPrice());
        dto.setTopProductStore(entity.getTopProductStore());
        dto.setTopProductImage(entity.getTopProductImage());
        dto.setTopProductId(entity.getTopProductId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
