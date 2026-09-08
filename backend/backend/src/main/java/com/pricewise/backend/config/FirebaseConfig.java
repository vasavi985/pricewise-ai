package com.pricewise.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String DEFAULT_PROJECT_ID = "pricewise-ai-be26e";

    @Value("${firebase.project-id:pricewise-ai-be26e}")
    private String projectId;

    @Value("${firebase.client-email:}")
    private String clientEmail;

    @Value("${firebase.private-key:}")
    private String privateKey;

    @Value("${google.application.credentials:}")
    private String googleAppCredentialsPath;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            FirebaseApp existing = FirebaseApp.getInstance();
            log.info("Using existing FirebaseApp instance [{}] for project [{}]", existing.getName(), existing.getOptions().getProjectId());
            return existing;
        }

        GoogleCredentials credentials = resolveCredentials();
        if (credentials == null) {
            log.warn("==========================================================================");
            log.warn("⚠️  FIREBASE CREDENTIALS NOT DETECTED");
            log.warn("Configure either GOOGLE_APPLICATION_CREDENTIALS or FIREBASE_PROJECT_ID,");
            log.warn("FIREBASE_CLIENT_EMAIL, and FIREBASE_PRIVATE_KEY in your .env or Render environment.");
            log.warn("The application will run with local in-memory fallback persistence until credentials are provided.");
            log.warn("==========================================================================");
            return null;
        }

        try {
            String effectiveProjectId = getEffectiveProjectId();
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(credentials);

            if (effectiveProjectId != null && !effectiveProjectId.trim().isEmpty()) {
                builder.setProjectId(effectiveProjectId.trim());
            }

            FirebaseApp app = FirebaseApp.initializeApp(builder.build());
            log.info("Successfully initialized FirebaseApp [{}] for project [{}]", app.getName(), effectiveProjectId);
            return app;
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public Firestore firestore(@Autowired(required = false) FirebaseApp firebaseApp) {
        if (firebaseApp == null && FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not available. Firestore falling back to local in-memory persistence mode.");
            return null;
        }

        try {
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
                || (resolveClientEmail() != null && resolvePrivateKey() != null);
    }

    private GoogleCredentials resolveCredentials() {
        try {
            // Option 1: File path or raw JSON via GOOGLE_APPLICATION_CREDENTIALS / googleAppCredentialsPath
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            String path = (envPath != null && !envPath.trim().isEmpty()) ? envPath : googleAppCredentialsPath;

            if (path != null && !path.trim().isEmpty()) {
                String trimmedPath = path.trim();
                // Check if GOOGLE_APPLICATION_CREDENTIALS actually contains the raw JSON secret content
                if (trimmedPath.startsWith("{") && trimmedPath.contains("\"private_key\"")) {
                    log.info("Loading Firebase credentials from raw JSON in GOOGLE_APPLICATION_CREDENTIALS");
                    return GoogleCredentials.fromStream(new ByteArrayInputStream(trimmedPath.getBytes(StandardCharsets.UTF_8)));
                }

                File file = new File(trimmedPath);
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
            String email = resolveClientEmail();
            String rawKey = resolvePrivateKey();
            String proj = getEffectiveProjectId();

            if (rawKey != null && !rawKey.trim().isEmpty()) {
                String trimmedKey = rawKey.trim();

                // If user pasted full Service Account JSON into FIREBASE_PRIVATE_KEY or FIREBASE_SERVICE_ACCOUNT
                if (trimmedKey.startsWith("{") && trimmedKey.contains("\"private_key\"")) {
                    log.info("Loading Firebase credentials from raw Service Account JSON");
                    return GoogleCredentials.fromStream(new ByteArrayInputStream(trimmedKey.getBytes(StandardCharsets.UTF_8)));
                }

                if (email != null && !email.trim().isEmpty()) {
                    String normalizedKey = normalizePrivateKey(trimmedKey);
                    String escapedKeyForJson = normalizedKey.replace("\"", "\\\"").replace("\n", "\\n");

                    // GoogleCredentials requires 'client_id', 'client_email', 'private_key' and 'private_key_id'
                    String json = "{\n" +
                            "  \"type\": \"service_account\",\n" +
                            "  \"project_id\": \"" + (proj != null ? proj.trim() : DEFAULT_PROJECT_ID) + "\",\n" +
                            "  \"private_key_id\": \"pricewise-admin-key\",\n" +
                            "  \"private_key\": \"" + escapedKeyForJson + "\",\n" +
                            "  \"client_email\": \"" + email.trim() + "\",\n" +
                            "  \"client_id\": \"pricewise-admin-client\"\n" +
                            "}";

                    log.info("Loading Firebase credentials from inline client email: {} for target project [{}]", email, proj);
                    return GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                }
            }

        } catch (Exception e) {
            log.error("Error resolving Firebase credentials: {}", e.getMessage());
        }

        return null;
    }

    private String normalizePrivateKey(String rawKey) {
        if (rawKey == null) return "";
        String key = rawKey.trim();

        // Strip surrounding double quotes or single quotes if present (e.g. from Render dashboard or .env file)
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            if (key.length() >= 2) {
                key = key.substring(1, key.length() - 1).trim();
            }
        }

        // Remove carriage returns (\r)
        key = key.replace("\\r", "").replace("\r", "");

        // Convert escaped \n to real newlines
        key = key.replace("\\n", "\n").trim();

        // Strip quotes again if double-wrapped
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            if (key.length() >= 2) {
                key = key.substring(1, key.length() - 1).trim();
            }
        }

        return key;
    }

    private String resolveClientEmail() {
        String envEmail = System.getenv("FIREBASE_CLIENT_EMAIL");
        if (envEmail != null && !envEmail.trim().isEmpty()) {
            return envEmail.trim();
        }
        String envEmailAlt = System.getenv("FIREBASE_EMAIL");
        if (envEmailAlt != null && !envEmailAlt.trim().isEmpty()) {
            return envEmailAlt.trim();
        }
        if (clientEmail != null && !clientEmail.trim().isEmpty()) {
            return clientEmail.trim();
        }
        return null;
    }

    private String resolvePrivateKey() {
        String envKey = System.getenv("FIREBASE_PRIVATE_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        String envKeyAlt1 = System.getenv("FIREBASE_SERVICE_ACCOUNT");
        if (envKeyAlt1 != null && !envKeyAlt1.trim().isEmpty()) {
            return envKeyAlt1.trim();
        }
        String envKeyAlt2 = System.getenv("FIREBASE_CREDENTIALS");
        if (envKeyAlt2 != null && !envKeyAlt2.trim().isEmpty()) {
            return envKeyAlt2.trim();
        }
        String envKeyAlt3 = System.getenv("FIREBASE_KEY");
        if (envKeyAlt3 != null && !envKeyAlt3.trim().isEmpty()) {
            return envKeyAlt3.trim();
        }
        if (privateKey != null && !privateKey.trim().isEmpty()) {
            return privateKey.trim();
        }
        return null;
    }

    public String getEffectiveProjectId() {
        String envProj = System.getenv("FIREBASE_PROJECT_ID");
        if (envProj != null && !envProj.trim().isEmpty()) {
            return envProj.trim();
        }
        String gcpProj = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (gcpProj != null && !gcpProj.trim().isEmpty()) {
            return gcpProj.trim();
        }
        String gcpProjAlt = System.getenv("GCP_PROJECT");
        if (gcpProjAlt != null && !gcpProjAlt.trim().isEmpty()) {
            return gcpProjAlt.trim();
        }
        if (projectId != null && !projectId.trim().isEmpty()) {
            return projectId.trim();
        }
        // Infer from client email if formatted as serviceaccount@<project-id>.iam.gserviceaccount.com
        String email = resolveClientEmail();
        if (email != null && email.contains("@") && email.contains(".iam.gserviceaccount.com")) {
            int atIdx = email.indexOf('@');
            int dotIdx = email.indexOf(".iam.gserviceaccount.com");
            if (atIdx >= 0 && dotIdx > atIdx) {
                return email.substring(atIdx + 1, dotIdx).trim();
            }
        }
        return DEFAULT_PROJECT_ID;
    }
}
