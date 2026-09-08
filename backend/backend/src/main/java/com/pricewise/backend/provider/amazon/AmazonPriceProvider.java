package com.pricewise.backend.provider.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(AmazonPriceProvider.class);
    private static final String DEFAULT_RAPIDAPI_HOST = "real-time-amazon-data.p.rapidapi.com";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes cache to avoid excessive API calls

    @Value("${rapidapi.key:}")
    private String rapidApiKey;

    @Value("${rapidapi.host:real-time-amazon-data.p.rapidapi.com}")
    private String rapidApiHost;

    @Value("${pricewise.providers.amazon.country:IN}")
    private String country;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private volatile boolean isUnavailable = false;
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final List<ProviderProductDTO> items;
        final long timestamp;

        CacheEntry(List<ProviderProductDTO> items) {
            this.items = items;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    @Autowired
    public AmazonPriceProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // Constructor for testing with mocked RestClient
    public AmazonPriceProvider(ObjectMapper objectMapper, RestClient restClient, String rapidApiKey, String rapidApiHost, String country) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.rapidApiKey = rapidApiKey;
        this.rapidApiHost = rapidApiHost;
        this.country = country;
    }

    @Override
    public String getStoreName() {
        return "AMAZON";
    }

    @Override
    public boolean isConfigured() {
        String key = resolveApiKey();
        return key != null && !key.trim().isEmpty();
    }

    @Override
    public String getStoreStatus() {
        if (!isConfigured()) {
            return "CONFIG_REQUIRED";
        }
        if (isUnavailable) {
            return "UNAVAILABLE";
        }
        return "LIVE";
    }

    @Override
    public String getRequiredConfig() {
        return "RAPIDAPI_KEY (Host: " + resolveApiHost() + ")";
    }

    @Override
    public String getDescription() {
        return "Amazon Real-Time Product & Price API via RapidAPI";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (!isConfigured()) {
            log.info("Amazon RapidAPI provider is not configured (RAPIDAPI_KEY is missing). Skipping live Amazon search for: '{}'", query);
            return Collections.emptyList();
        }

        String safeQuery = query.trim();
        String host = resolveApiHost();
        String targetCountry = resolveCountry();
        String cacheKey = safeQuery.toLowerCase() + ":" + targetCountry;

        // Check in-memory cache to prevent burning RapidAPI quota
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning {} cached Amazon results for query '{}'", cached.items.size(), safeQuery);
            return cached.items;
        }

        try {
            log.info("Querying Real-Time Amazon Data API on [{}] for query: '{}' (country: {})", host, safeQuery, targetCountry);
            String url = String.format("https://%s/search?query={query}&page=1&country={country}", host);

            String response = restClient.get()
                    .uri(url, safeQuery, targetCountry)
                    .header("x-rapidapi-key", resolveApiKey())
                    .header("x-rapidapi-host", host)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.trim().isEmpty()) {
                log.warn("Real-Time Amazon Data API returned empty response for query: '{}'", safeQuery);
                return Collections.emptyList();
            }

            List<ProviderProductDTO> results = parseSearchResponse(response);
            this.isUnavailable = false;

            // Cache up to 100 queries
            if (searchCache.size() > 100) {
                searchCache.clear();
            }
            searchCache.put(cacheKey, new CacheEntry(results));

            log.info("Successfully fetched {} Amazon products for query: '{}'", results.size(), safeQuery);
            return results;

        } catch (RestClientResponseException e) {
            log.error("Real-Time Amazon Data API returned HTTP error: status={}, message={}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode().is5xxServerError()) {
                this.isUnavailable = true;
            }
            return Collections.emptyList();
        } catch (ResourceAccessException e) {
            log.error("Real-Time Amazon Data API connection/timeout error: {}", e.getMessage());
            this.isUnavailable = true;
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error querying Real-Time Amazon Data API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (!isConfigured()) {
            log.info("Amazon RapidAPI provider is not configured. Price check unavailable.");
            return null;
        }

        String asin = extractAsin(storeProductId, productUrl);
        if (asin == null || asin.trim().isEmpty()) {
            log.warn("Cannot fetch Amazon product details without valid ASIN. storeProductId={}, productUrl={}", storeProductId, productUrl);
            return null;
        }

        String host = resolveApiHost();
        String targetCountry = resolveCountry();

        try {
            log.info("Querying Real-Time Amazon Data API product details for ASIN: {} (country: {})", asin, targetCountry);
            String url = String.format("https://%s/product-details?asin={asin}&country={country}", host);

            String response = restClient.get()
                    .uri(url, asin, targetCountry)
                    .header("x-rapidapi-key", resolveApiKey())
                    .header("x-rapidapi-host", host)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                dataNode = root;
            }

            String title = dataNode.path("product_title").asText(null);
            String liveUrl = dataNode.path("product_url").asText(productUrl != null ? productUrl : "https://www.amazon.in/dp/" + asin);
            String photo = dataNode.path("product_photo").asText(null);

            String priceStr = dataNode.path("product_price").asText(null);
            if (priceStr == null || priceStr.trim().isEmpty()) {
                priceStr = dataNode.path("product_minimum_offer_price").asText(null);
            }
            if (priceStr == null || priceStr.trim().isEmpty()) {
                priceStr = dataNode.path("product_original_price").asText(null);
            }

            Double price = parsePrice(priceStr);
            if (price == null || price <= 0) {
                return null;
            }

            String currency = dataNode.path("currency").asText(targetCountry.equalsIgnoreCase("IN") ? "INR" : "USD");
            Double rating = parseRating(dataNode.path("product_star_rating").asText(null));
            String availability = dataNode.path("product_availability").asText("IN_STOCK");

            return new ProviderProductDTO(
                    "AMAZON",
                    asin,
                    title != null ? title.trim() : "Amazon Product (" + asin + ")",
                    title != null ? extractCanonicalName(title) : "Amazon Product (" + asin + ")",
                    title != null ? extractBrand(title) : "Amazon",
                    "",
                    "Electronics & Consumer Goods",
                    "Amazon Verified Listing",
                    photo,
                    liveUrl,
                    price,
                    currency,
                    availability,
                    "LIVE",
                    rating
            );

        } catch (Exception e) {
            log.error("Failed to fetch Amazon product details for ASIN {}: {}", asin, e.getMessage());
            return null;
        }
    }

    public List<ProviderProductDTO> parseSearchResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            String status = root.path("status").asText("OK");
            if ("ERROR".equalsIgnoreCase(status)) {
                log.warn("Real-Time Amazon Data API reported error: {}", root.path("message").asText(""));
                return Collections.emptyList();
            }

            JsonNode dataNode = root.path("data");
            JsonNode productsNode = dataNode.path("products");

            if (!productsNode.isArray() || productsNode.isEmpty()) {
                if (root.path("products").isArray()) {
                    productsNode = root.path("products");
                } else {
                    return Collections.emptyList();
                }
            }

            String targetCountry = resolveCountry();
            String defaultCurrency = targetCountry.equalsIgnoreCase("IN") ? "INR" : "USD";

            List<ProviderProductDTO> list = new ArrayList<>();
            for (JsonNode item : productsNode) {
                String asin = item.path("asin").asText(null);
                String title = item.path("product_title").asText(null);
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                String productUrl = item.path("product_url").asText(null);
                if (productUrl == null || productUrl.trim().isEmpty()) {
                    if (asin != null && !asin.trim().isEmpty()) {
                        productUrl = "https://www.amazon.in/dp/" + asin.trim();
                    }
                }

                String photoUrl = item.path("product_photo").asText(null);
                if (photoUrl == null || photoUrl.trim().isEmpty()) {
                    photoUrl = item.path("product_main_image_url").asText(null);
                }

                String priceStr = item.path("product_price").asText(null);
                if (priceStr == null || priceStr.trim().isEmpty()) {
                    priceStr = item.path("product_minimum_offer_price").asText(null);
                }
                if (priceStr == null || priceStr.trim().isEmpty()) {
                    priceStr = item.path("product_original_price").asText(null);
                }

                Double price = parsePrice(priceStr);
                if (price == null || price <= 0) {
                    continue; // Preserve real prices only - never fabricate or return non-priced items
                }

                String currency = item.path("currency").asText(null);
                if (currency == null || currency.trim().isEmpty()) {
                    currency = defaultCurrency;
                }

                Double rating = parseRating(item.path("product_star_rating").asText(null));
                String storeProductId = (asin != null && !asin.trim().isEmpty()) ? asin.trim() : "AMZ-" + Math.abs(title.hashCode());
                String brand = extractBrand(title);
                String canonical = extractCanonicalName(title);

                ProviderProductDTO dto = new ProviderProductDTO(
                        "AMAZON",
                        storeProductId,
                        title.trim(),
                        canonical,
                        brand,
                        "",
                        "Electronics & Consumer Goods",
                        "Amazon Real-Time Listing: " + title.trim(),
                        photoUrl,
                        productUrl,
                        price,
                        currency,
                        "IN_STOCK",
                        "LIVE",
                        rating
                );

                list.add(dto);
            }

            return list;
        } catch (Exception e) {
            log.error("Failed to parse Real-Time Amazon Data API search response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public static Double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return null;
        }

        String s = priceStr.replace("&nbsp;", " ").replace("\u00a0", " ").trim();
        // Remove currency words and prefixes like "Rs.", "INR", "USD", etc.
        s = s.replaceAll("(?i)\\b(rs|inr|usd|eur|gbp)\\.?\\s*", "").trim();
        s = s.replaceAll("[^0-9.,]", "").trim();
        if (s.isEmpty()) {
            return null;
        }

        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');

        if (lastDot > lastComma) {
            // e.g. "1,29,990.00" or "1099.99"
            // Commas and preceding dots are thousands separators
            String integerPart = s.substring(0, lastDot).replaceAll("[.,]", "");
            String decimalPart = s.substring(lastDot + 1);
            s = integerPart + "." + decimalPart;
        } else if (lastComma > lastDot) {
            // e.g. "58,999" (Indian/US thousands) or "1.299,50" (European decimal)
            String afterComma = s.substring(lastComma + 1);
            if (afterComma.length() == 3 && lastDot == -1) {
                s = s.replace(",", "");
            } else if (afterComma.length() == 2 && lastDot != -1) {
                String integerPart = s.substring(0, lastComma).replaceAll("[.,]", "");
                s = integerPart + "." + afterComma;
            } else {
                s = s.replace(",", "");
            }
        }

        try {
            double val = Double.parseDouble(s);
            return (val > 0) ? Math.round(val * 100.0) / 100.0 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseRating(String ratingStr) {
        if (ratingStr == null || ratingStr.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = ratingStr.replaceAll("[^0-9.]", " ").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                double r = Double.parseDouble(parts[0]);
                return (r >= 0 && r <= 5.0) ? r : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String extractAsin(String storeProductId, String productUrl) {
        if (storeProductId != null && storeProductId.trim().matches("^[A-Z0-9]{10}$")) {
            return storeProductId.trim();
        }
        if (productUrl != null && !productUrl.trim().isEmpty()) {
            Pattern p = Pattern.compile("/(?:dp|product|gp/product)/([A-Z0-9]{10})");
            Matcher m = p.matcher(productUrl);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    public static String extractBrand(String title) {
        if (title == null || title.trim().isEmpty()) return "Amazon";
        String lower = title.toLowerCase();
        if (lower.startsWith("apple ") || lower.contains("apple")) return "Apple";
        if (lower.startsWith("samsung ") || lower.contains("samsung")) return "Samsung";
        if (lower.startsWith("sony ") || lower.contains("sony")) return "Sony";
        if (lower.startsWith("dell ") || lower.contains("dell")) return "Dell";
        if (lower.startsWith("hp ") || lower.contains("hp")) return "HP";
        if (lower.startsWith("lenovo ") || lower.contains("lenovo")) return "Lenovo";
        if (lower.startsWith("asus ") || lower.contains("asus")) return "Asus";
        if (lower.startsWith("oneplus ") || lower.contains("oneplus")) return "OnePlus";
        if (lower.startsWith("boat ") || lower.contains("boat")) return "boAt";
        String[] words = title.trim().split("\\s+");
        if (words.length > 0 && words[0].matches("^[A-Za-z0-9]+$")) {
            return words[0];
        }
        return "Amazon";
    }

    public static String extractCanonicalName(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Amazon Product";
        }
        return title.trim().replaceAll("[\\.\"]+$", "");
    }

    public void clearCache() {
        searchCache.clear();
    }

    private String resolveApiKey() {
        String envKey = System.getenv("RAPIDAPI_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        if (rapidApiKey != null && !rapidApiKey.trim().isEmpty()) {
            return rapidApiKey.trim();
        }
        return null;
    }

    private String resolveApiHost() {
        String envHost = System.getenv("RAPIDAPI_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return envHost.trim();
        }
        if (rapidApiHost != null && !rapidApiHost.trim().isEmpty()) {
            return rapidApiHost.trim();
        }
        return DEFAULT_RAPIDAPI_HOST;
    }

    private String resolveCountry() {
        String envCountry = System.getenv("AMAZON_COUNTRY");
        if (envCountry != null && !envCountry.trim().isEmpty()) {
            return envCountry.trim();
        }
        if (country != null && !country.trim().isEmpty()) {
            return country.trim();
        }
        return "IN";
    }
}

