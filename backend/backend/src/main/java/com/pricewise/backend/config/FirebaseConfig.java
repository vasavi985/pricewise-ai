package com.pricewise.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.project-id:}")
    private String projectId;

    @Value("${firebase.client-email:}")
    private String clientEmail;

    @Value("${firebase.private-key:}")
    private String privateKey;

    @Value("${google.application.credentials:}")
    private String googleAppCredentialsPath;

    @Bean
    public Firestore firestore() {
        try {
            GoogleCredentials credentials = resolveCredentials();
            if (credentials == null) {
                log.warn("==========================================================================");
                log.warn("⚠️  FIREBASE CREDENTIALS NOT DETECTED");
                log.warn("Configure either GOOGLE_APPLICATION_CREDENTIALS or FIREBASE_PROJECT_ID,");
                log.warn("FIREBASE_CLIENT_EMAIL, and FIREBASE_PRIVATE_KEY in your .env or environment.");
                log.warn("The application will run with local in-memory fallback persistence until credentials are provided.");
                log.warn("==========================================================================");
                return null;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setCredentials(credentials);

                String effectiveProjectId = getEffectiveProjectId();
                if (effectiveProjectId != null && !effectiveProjectId.trim().isEmpty()) {
                    builder.setProjectId(effectiveProjectId.trim());
                }

                FirebaseApp.initializeApp(builder.build());
                log.info("Successfully initialized FirebaseApp for project [{}]", effectiveProjectId);
            }

            Firestore firestore = FirestoreClient.getFirestore();
            log.info("Cloud Firestore default database client successfully established.");
            return firestore;

        } catch (Exception e) {
            log.error("Failed to initialize Firebase Firestore client: {}", e.getMessage());
            log.warn("Falling back to local in-memory persistence mode.");
            return null;
        }
    }

    public boolean isConfigured() {
        return (googleAppCredentialsPath != null && !googleAppCredentialsPath.trim().isEmpty() && new File(googleAppCredentialsPath).exists())
                || (clientEmail != null && !clientEmail.trim().isEmpty() && privateKey != null && !privateKey.trim().isEmpty());
    }

    private GoogleCredentials resolveCredentials() {
        try {
            // Option 1: File path via GOOGLE_APPLICATION_CREDENTIALS or property
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            String path = (envPath != null && !envPath.trim().isEmpty()) ? envPath : googleAppCredentialsPath;

            if (path != null && !path.trim().isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    log.info("Loading Firebase credentials from file: {}", file.getAbsolutePath());
                    try (FileInputStream fis = new FileInputStream(file)) {
                        return GoogleCredentials.fromStream(fis);
                    }
                } else {
                    log.warn("GOOGLE_APPLICATION_CREDENTIALS path specified but file does not exist: {}", path);
                }
            }

            // Option 2: Inline environment variables / properties
            String envEmail = System.getenv("FIREBASE_CLIENT_EMAIL");
            String email = (envEmail != null && !envEmail.trim().isEmpty()) ? envEmail : clientEmail;

            String envKey = System.getenv("FIREBASE_PRIVATE_KEY");
            String key = (envKey != null && !envKey.trim().isEmpty()) ? envKey : privateKey;

            String envProj = System.getenv("FIREBASE_PROJECT_ID");
            String proj = (envProj != null && !envProj.trim().isEmpty()) ? envProj : projectId;

            if (email != null && !email.trim().isEmpty() && key != null && !key.trim().isEmpty()) {
                // Safely handle escaped newlines in the private key string
                String normalizedKey = key.replace("\\n", "\n").trim();
                String escapedKeyForJson = normalizedKey.replace("\"", "\\\"").replace("\n", "\\n");

                String json = String.format("{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"%s\",\n" +
                        "  \"client_email\": \"%s\",\n" +
                        "  \"private_key\": \"%s\"\n" +
                        "}", (proj != null ? proj.trim() : ""), email.trim(), escapedKeyForJson);

                log.info("Loading Firebase credentials from inline client email: {}", email);
                return GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            }

        } catch (Exception e) {
            log.error("Error resolving Firebase credentials: {}", e.getMessage());
        }

        return null;
    }

    private String getEffectiveProjectId() {
        String envProj = System.getenv("FIREBASE_PROJECT_ID");
        if (envProj != null && !envProj.trim().isEmpty()) {
            return envProj.trim();
        }
        if (projectId != null && !projectId.trim().isEmpty()) {
            return projectId.trim();
        }
        return null;
    }
}
