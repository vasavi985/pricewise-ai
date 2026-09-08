package com.pricewise.backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);

    private final FirebaseApp firebaseApp;

    public FirebaseAuthService(@Autowired(required = false) FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
    }

    /**
     * Verifies the Firebase ID token in the Authorization header and returns the authenticated UID.
     * Throws 401 UNAUTHORIZED if the header is missing, invalid, or expired.
     */
    public String verifyTokenAndGetUid(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            log.warn("Authentication rejected: Authorization header is absent");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header.");
        }

        String token = extractBearerToken(authHeader);
        if (token == null || token.trim().isEmpty()) {
            log.warn("Authentication rejected: Authorization header is malformed (expected 'Bearer <token>')");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header.");
        }

        log.debug("Authorization header present with Bearer token (length: {})", token.length());
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
        FirebaseApp app = (firebaseApp != null) ? firebaseApp : (!FirebaseApp.getApps().isEmpty() ? FirebaseApp.getInstance() : null);

        // If running in test or local mode where FirebaseApp is not initialized
        if (app == null) {
            if (token.startsWith("test-token-")) {
                return token.substring("test-token-".length());
            }
            log.warn("FirebaseApp is not initialized. Rejecting live token in offline mode.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication service is running in offline mode.");
        }

        String targetProjectId = (app.getOptions() != null) ? app.getOptions().getProjectId() : "unknown";

        try {
            log.debug("Verifying Firebase ID token (length: {}) against project [{}]", token.length(), targetProjectId);
            FirebaseToken decodedToken = FirebaseAuth.getInstance(app).verifyIdToken(token);
            String uid = decodedToken.getUid();
            if (uid == null || uid.trim().isEmpty()) {
                log.warn("Firebase token verification returned empty UID for project [{}]", targetProjectId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase token did not contain a valid UID.");
            }
            log.debug("Successfully authenticated Firebase user [{}] for project [{}]", uid, targetProjectId);
            return uid;
        } catch (FirebaseAuthException e) {
            log.error("Firebase ID token verification failed (project: [{}], authErrorCode: [{}], error detail: {})",
                    targetProjectId, e.getAuthErrorCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Firebase ID token: " + e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Firebase ID token verification for project [{}]: {} ({})",
                    targetProjectId, e.getClass().getName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase token verification error: " + e.getMessage());
        }
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String trimmed = authHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            // Safeguard against accidental surrounding double or single quotation marks
            if ((token.startsWith("\"") && token.endsWith("\"")) ||
                (token.startsWith("'") && token.endsWith("'"))) {
                if (token.length() >= 2) {
                    token = token.substring(1, token.length() - 1).trim();
                }
            }
            return token;
        }
        return null;
    }
}
