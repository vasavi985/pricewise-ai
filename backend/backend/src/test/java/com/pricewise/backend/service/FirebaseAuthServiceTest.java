package com.pricewise.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseAuthServiceTest {

    private FirebaseAuthService service;

    @BeforeEach
    void setUp() {
        // Passing null FirebaseApp to simulate offline / unit test mode
        service = new FirebaseAuthService(null);
    }

    @Test
    void testMissingAuthHeaderThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.verifyTokenAndGetUid(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Missing Authorization header"));
    }

    @Test
    void testEmptyAuthHeaderThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.verifyTokenAndGetUid("   "));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testMalformedAuthHeaderThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.verifyTokenAndGetUid("Basic abc123xyz"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("malformed Authorization header"));
    }

    @Test
    void testOfflineTestTokenSucceeds() {
        String uid = service.verifyTokenAndGetUid("Bearer test-token-user-12345");
        assertEquals("user-12345", uid);
    }

    @Test
    void testOfflineTestTokenWithSurroundingQuotesSucceeds() {
        String uid = service.verifyTokenAndGetUid("Bearer \"test-token-quoted-user\"");
        assertEquals("quoted-user", uid);
    }

    @Test
    void testExtractUidOrNullWithMissingHeaderReturnsNull() {
        assertNull(service.extractUidOrNull(null));
        assertNull(service.extractUidOrNull(""));
    }

    @Test
    void testExtractUidOrNullWithValidTestTokenReturnsUid() {
        assertEquals("search-user-789", service.extractUidOrNull("Bearer test-token-search-user-789"));
    }

    @Test
    void testLiveTokenInOfflineModeThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.verifyTokenAndGetUid("Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.live-token.sig"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("offline mode"));
    }
}
