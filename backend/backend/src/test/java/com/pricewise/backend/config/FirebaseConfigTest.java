package com.pricewise.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseConfigTest {

    @AfterEach
    void tearDown() {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            app.delete();
        }
    }

    private String generatePemKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        return "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";
    }

    @Test
    void testEffectiveProjectIdDefault() {
        FirebaseConfig config = new FirebaseConfig();
        assertEquals("pricewise-ai-be26e", config.getEffectiveProjectId());
    }

    @Test
    void testCredentialsResolutionWithEscapedNewlines() throws Exception {
        String pem = generatePemKey();
        String escaped = pem.replace("\n", "\\n");

        // Surround with quotes as often done in .env or Render dashboard
        String quoted = "\"" + escaped + "\"";

        // Test credentials building logic as in FirebaseConfig
        String cleanedKey = quoted.trim();
        if ((cleanedKey.startsWith("\"") && cleanedKey.endsWith("\"")) ||
            (cleanedKey.startsWith("'") && cleanedKey.endsWith("'"))) {
            cleanedKey = cleanedKey.substring(1, cleanedKey.length() - 1).trim();
        }
        cleanedKey = cleanedKey.replace("\\r", "").replace("\r", "");
        cleanedKey = cleanedKey.replace("\\n", "\n").trim();
        if ((cleanedKey.startsWith("\"") && cleanedKey.endsWith("\"")) ||
            (cleanedKey.startsWith("'") && cleanedKey.endsWith("'"))) {
            cleanedKey = cleanedKey.substring(1, cleanedKey.length() - 1).trim();
        }

        String escapedKeyForJson = cleanedKey.replace("\"", "\\\"").replace("\n", "\\n");
        String json = "{\n" +
                "  \"type\": \"service_account\",\n" +
                "  \"project_id\": \"pricewise-ai-be26e\",\n" +
                "  \"private_key_id\": \"pricewise-admin-key\",\n" +
                "  \"private_key\": \"" + escapedKeyForJson + "\",\n" +
                "  \"client_email\": \"firebase-adminsdk@pricewise-ai-be26e.iam.gserviceaccount.com\",\n" +
                "  \"client_id\": \"pricewise-admin-client\"\n" +
                "}";

        GoogleCredentials creds = GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(creds);
        assertTrue(creds instanceof ServiceAccountCredentials);
        assertEquals("pricewise-ai-be26e", ((ServiceAccountCredentials) creds).getProjectId());

        // Test FirebaseApp initialization with resolved credentials
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(creds)
                .setProjectId("pricewise-ai-be26e")
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        assertEquals("pricewise-ai-be26e", app.getOptions().getProjectId());
    }
}
