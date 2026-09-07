package com.pricewise.backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);

    /**
     * Verifies the Firebase ID token in the Authorization header and returns the authenticated UID.
     * Throws 401 UNAUTHORIZED if the header is missing, invalid, or expired.
     */
    public String verifyTokenAndGetUid(String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null || token.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header.");
        }

        return verifyToken(token);
    }

    /**
     * Attempts to verify the token if present, returning the UID, or null if unauthenticated.
     * Throws 401 only if an invalid token was explicitly provided.
     */
    public String extractUidOrNull(String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        try {
            return verifyToken(token);
        } catch (Exception e) {
            log.warn("Optional auth token verification failed: {}", e.getMessage());
            return null;
        }
    }

    private String verifyToken(String token) {
        // If running in test or local mode where FirebaseApp is not initialized
        if (FirebaseApp.getApps().isEmpty()) {
            if (token.startsWith("test-token-")) {
                return token.substring("test-token-".length());
            }
            log.warn("FirebaseApp is not initialized. Rejecting live token in offline mode.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication service is running in offline mode.");
        }

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decodedToken.getUid();
            if (uid == null || uid.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase token did not contain a valid UID.");
            }
            return uid;
        } catch (Exception e) {
            log.error("Firebase ID token verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Firebase ID token: " + e.getMessage());
        }
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String trimmed = authHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }
}
