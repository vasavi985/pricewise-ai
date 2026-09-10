package com.pricewise.backend.provider.flipkart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlipkartPriceProvider implements PriceProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FlipkartPriceProvider.class);
    private static final String DEFAULT_RAPIDAPI_HOST = "real-time-flipkart-data2.p.rapidapi.com";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes cache to avoid excessive API calls
    private static final int HARD_TIMEOUT_SECONDS = 8; // 8-second deadline to accommodate scraper latency

    @Value("${pricewise.providers.flipkart.api-key:}")
    private String flipkartApiKey;

    @Value("${pricewise.providers.flipkart.host:real-time-flipkart-data2.p.rapidapi.com}")
    private String flipkartApiHost;

    @Value("${pricewise.providers.flipkart.search-path:/search}")
    private String searchPath = "/search";

    @Value("${pricewise.providers.flipkart.timeout-seconds:8}")
    private int timeoutSeconds = HARD_TIMEOUT_SECONDS;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ExecutorService executorService;

    private volatile boolean isUnavailable = false;
    private volatile long lastFailureTime = 0;
    private static final long UNAVAILABLE_COOLDOWN_MS = 60 * 1000; // 1-minute auto-recovery window
    private volatile String lastError = null;
    private volatile String discoveredSearchPath = null;

    public static final List<String> CANDIDATE_SEARCH_PATHS = List.of(
            "/search",
            "/products",
            "/product-search",
            "/products/search",
            "/search-products",
            "/search-product",
            "/items",
            "/item-search",
            "/flipkart/search",
            "/flipkart/products",
            "/flipkart-search",
            "/searchByKeyword",
            "/search_by_keyword",
            "/api/products",
            "/api/search",
            "/v1/products",
            "/v1/search",
            "/v2/products",
            "/v2/search",
            "/"
    );

    public String getActiveSearchPath() {
        if (discoveredSearchPath != null && !discoveredSearchPath.trim().isEmpty()) {
            return discoveredSearchPath.trim();
        }
        return (searchPath != null && !searchPath.trim().isEmpty()) ? searchPath.trim() : "/search";
    }

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
    public FlipkartPriceProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        this.executorService = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "flipkart-provider-pool");
            t.setDaemon(true);
            return t;
        });
    }

    // Constructor for testing with mocked RestClient
    public FlipkartPriceProvider(ObjectMapper objectMapper, RestClient restClient, String flipkartApiKey, String flipkartApiHost) {
        this(objectMapper, restClient, flipkartApiKey, flipkartApiHost, Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "flipkart-provider-test-pool");
            t.setDaemon(true);
            return t;
        }), HARD_TIMEOUT_SECONDS);
    }

    public FlipkartPriceProvider(ObjectMapper objectMapper, RestClient restClient, String flipkartApiKey, String flipkartApiHost, ExecutorService executorService) {
        this(objectMapper, restClient, flipkartApiKey, flipkartApiHost, executorService, HARD_TIMEOUT_SECONDS);
    }

    public FlipkartPriceProvider(ObjectMapper objectMapper, RestClient restClient, String flipkartApiKey, String flipkartApiHost, ExecutorService executorService, int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.flipkartApiKey = flipkartApiKey;
        this.flipkartApiHost = flipkartApiHost;
        this.executorService = executorService != null ? executorService : Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "flipkart-provider-test-pool");
            t.setDaemon(true);
            return t;
        });
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : HARD_TIMEOUT_SECONDS;
    }

    @Override
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public String getStoreName() {
        return "FLIPKART";
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
        if (isUnavailable && (System.currentTimeMillis() - lastFailureTime < UNAVAILABLE_COOLDOWN_MS)) {
            return "UNAVAILABLE";
        }
        if (isUnavailable && (System.currentTimeMillis() - lastFailureTime >= UNAVAILABLE_COOLDOWN_MS)) {
            isUnavailable = false; // Auto-recover to allow next query
        }
        return "LIVE";
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public String getRequiredConfig() {
        return "FLIPKART_API_KEY (RapidAPI Real-Time Flipkart Data)";
    }

    @Override
    public String getDescription() {
        return "Flipkart Real-Time Product & Price API via RapidAPI";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (!isConfigured()) {
            log.info("Flipkart RapidAPI provider is not configured (FLIPKART_API_KEY is missing). Skipping live Flipkart search for: '{}'", query);
            return Collections.emptyList();
        }

        String safeQuery = query.trim();
        String host = resolveApiHost();
        String cacheKey = safeQuery.toLowerCase();

        // Check in-memory cache to prevent burning RapidAPI quota
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning {} cached Flipkart results for query '{}'", cached.items.size(), safeQuery);
            return cached.items;
        }

        CompletableFuture<List<ProviderProductDTO>> future = CompletableFuture.supplyAsync(() -> {
            final String currentPath = getActiveSearchPath();
            try {
                log.info("Querying Real-Time Flipkart Data API on [{}] at path [{}] for query: '{}'", host, currentPath, safeQuery);
                String response = executeSearchRequest(host, currentPath, safeQuery);

                if (response == null || response.trim().isEmpty()) {
                    log.warn("Real-Time Flipkart Data API returned empty response on path [{}] for query: '{}'", currentPath, safeQuery);
                    return Collections.<ProviderProductDTO>emptyList();
                }

                List<ProviderProductDTO> results = parseSearchResponse(response);
                this.isUnavailable = false;
                this.lastError = null;
                this.discoveredSearchPath = currentPath;

                // Cache up to 100 queries
                if (searchCache.size() > 100) {
                    searchCache.clear();
                }
                searchCache.put(cacheKey, new CacheEntry(results));

                log.info("Flipkart search query = '{}' | Path = [{}] | HTTP status = 200 | Mapped PriceWise results = {}", safeQuery, currentPath, results.size());
                return results;

            } catch (RestClientResponseException e) {
                String safeSummary = safeErrorSummary(e.getResponseBodyAsString());
                log.error("Real-Time Flipkart Data API returned HTTP error on path [{}]: status={}, body={}", currentPath, e.getStatusCode(), safeSummary);

                // If primary search endpoint was 404, probe other candidate search paths
                if (e.getStatusCode().value() == 404) {
                    for (String candidate : CANDIDATE_SEARCH_PATHS) {
                        if (!candidate.equalsIgnoreCase(currentPath)) {
                            try {
                                log.info("Attempting candidate search endpoint {} on [{}] for query: '{}'", candidate, host, safeQuery);
                                String candidateResponse = executeSearchRequest(host, candidate, safeQuery);
                                if (candidateResponse != null && !candidateResponse.trim().isEmpty()) {
                                    List<ProviderProductDTO> results = parseSearchResponse(candidateResponse);
                                    this.discoveredSearchPath = candidate;
                                    this.isUnavailable = false;
                                    this.lastError = null;
                                    log.info("Successfully discovered active Flipkart search endpoint '{}' on [{}] (returned {} results)", candidate, host, results.size());
                                    searchCache.put(cacheKey, new CacheEntry(results));
                                    return results;
                                }
                            } catch (RestClientResponseException candidateEx) {
                                log.debug("Candidate {} returned HTTP {}", candidate, candidateEx.getStatusCode());
                            } catch (Exception candidateEx) {
                                log.debug("Candidate {} failed: {}", candidate, candidateEx.getMessage());
                            }
                        }
                    }
                }

                this.isUnavailable = true;
                this.lastFailureTime = System.currentTimeMillis();
                this.lastError = "HTTP " + e.getStatusCode().value() + " (" + host + " at " + currentPath + "): " + safeSummary;
                return Collections.<ProviderProductDTO>emptyList();
            } catch (ResourceAccessException e) {
                log.error("Real-Time Flipkart Data API connection/timeout error on [{}]: {}", host, e.getMessage());
                this.isUnavailable = true;
                this.lastFailureTime = System.currentTimeMillis();
                this.lastError = "Connection error (" + host + "): " + e.getMessage();
                return Collections.<ProviderProductDTO>emptyList();
            } catch (Exception e) {
                log.error("Unexpected error querying Real-Time Flipkart Data API on [{}]: {}", host, e.getMessage());
                this.isUnavailable = true;
                this.lastFailureTime = System.currentTimeMillis();
                this.lastError = "Provider error (" + host + "): " + e.getMessage();
                return Collections.<ProviderProductDTO>emptyList();
            }
        }, executorService);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Flipkart RapidAPI query for '{}' exceeded hard timeout of {}s.", safeQuery, timeoutSeconds);
            this.isUnavailable = true;
            this.lastFailureTime = System.currentTimeMillis();
            this.lastError = "Query exceeded timeout of " + timeoutSeconds + "s";
            return Collections.emptyList();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Flipkart RapidAPI query for '{}' was interrupted.", safeQuery);
            this.isUnavailable = true;
            this.lastFailureTime = System.currentTimeMillis();
            this.lastError = "Query was interrupted";
            return Collections.emptyList();
        } catch (ExecutionException e) {
            log.error("Execution exception during Flipkart RapidAPI query for '{}': {}", safeQuery, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            this.isUnavailable = true;
            this.lastFailureTime = System.currentTimeMillis();
            this.lastError = "Execution exception: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return Collections.emptyList();
        }
    }

    private String executeSearchRequest(String host, String path, String safeQuery) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path(path)
                        .queryParam("query", safeQuery)
                        .queryParam("q", safeQuery)
                        .queryParam("page", "1")
                        .build())
                .header("x-rapidapi-key", resolveApiKey())
                .header("x-rapidapi-host", host)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PriceWise-AI/1.0")
                .retrieve()
                .body(String.class);
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (!isConfigured()) {
            log.info("Flipkart RapidAPI provider is not configured. Price check unavailable.");
            return null;
        }

        String pid = extractPid(storeProductId, productUrl);
        if (pid == null || pid.trim().isEmpty()) {
            log.warn("Cannot fetch Flipkart product details without valid PID. storeProductId={}, productUrl={}", storeProductId, productUrl);
            return null;
        }

        String host = resolveApiHost();

        CompletableFuture<ProviderProductDTO> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Querying Real-Time Flipkart Data API product details for PID: {}", pid);

                String response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host(host)
                                .path("/product-details")
                                .queryParam("productId", pid)
                                .build())
                        .header("x-rapidapi-key", resolveApiKey())
                        .header("x-rapidapi-host", host)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PriceWise-AI/1.0")
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

                String title = extractField(dataNode, "product_title", "title", "name", "product_name");
                String liveUrl = extractField(dataNode, "product_url", "url", "link");
                if (liveUrl == null || liveUrl.trim().isEmpty()) {
                    liveUrl = productUrl != null ? productUrl : "https://www.flipkart.com/item/p/itm?pid=" + pid;
                }
                String photo = extractField(dataNode, "product_photo", "product_image", "thumbnail", "image");

                String priceStr = extractField(dataNode, "product_price", "price", "current_price", "special_price", "product_minimum_offer_price");
                Double price = parsePrice(priceStr);
                if (price == null || price <= 0) {
                    if (dataNode.path("price").isNumber()) {
                        price = dataNode.path("price").asDouble();
                    }
                }
                if (price == null || price <= 0) {
                    return null;
                }

                String currency = extractField(dataNode, "currency");
                if (currency == null || currency.trim().isEmpty()) {
                    currency = "INR";
                }

                Double rating = parseRating(extractField(dataNode, "product_rating", "rating", "product_star_rating"));
                String availability = extractField(dataNode, "product_availability", "availability", "stock");
                if (availability == null || availability.trim().isEmpty()) {
                    availability = "IN_STOCK";
                }

                return new ProviderProductDTO(
                        "FLIPKART",
                        pid,
                        title != null ? title.trim() : "Flipkart Product (" + pid + ")",
                        title != null ? extractCanonicalName(title) : "Flipkart Product (" + pid + ")",
                        title != null ? extractBrand(title) : "Flipkart",
                        "",
                        "Electronics & Consumer Goods",
                        "Flipkart Verified Listing",
                        photo,
                        liveUrl,
                        price,
                        currency,
                        availability,
                        "LIVE",
                        rating
                );

            } catch (Exception e) {
                log.error("Failed to fetch Flipkart product details for PID {}: {}", pid, e.getMessage());
                return null;
            }
        }, executorService);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Flipkart product details for PID {} exceeded hard timeout of {}s.", pid, timeoutSeconds);
            return null;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
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
                String errorMsg = root.path("message").asText(null);
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = root.path("error").path("message").asText("Unknown API error");
                }
                log.warn("Real-Time Flipkart Data API reported error: {}", errorMsg);
                this.isUnavailable = true;
                return Collections.emptyList();
            }

            JsonNode productsNode = null;
            if (root.isArray()) {
                productsNode = root;
            } else if (root.path("data").path("products").isArray()) {
                productsNode = root.path("data").path("products");
            } else if (root.path("products").isArray()) {
                productsNode = root.path("products");
            } else if (root.path("data").path("items").isArray()) {
                productsNode = root.path("data").path("items");
            } else if (root.path("items").isArray()) {
                productsNode = root.path("items");
            } else if (root.path("data").path("search_results").isArray()) {
                productsNode = root.path("data").path("search_results");
            } else if (root.path("search_results").isArray()) {
                productsNode = root.path("search_results");
            } else if (root.path("data").isArray()) {
                productsNode = root.path("data");
            } else if (root.path("result").isArray()) {
                productsNode = root.path("result");
            } else if (root.path("results").isArray()) {
                productsNode = root.path("results");
            } else if (root.path("response").path("products").isArray()) {
                productsNode = root.path("response").path("products");
            } else if (root.path("response").path("data").isArray()) {
                productsNode = root.path("response").path("data");
            }

            if (productsNode == null || productsNode.isEmpty()) {
                productsNode = findFirstProductArray(root);
            }

            if (productsNode == null || productsNode.isEmpty()) {
                return Collections.emptyList();
            }

            List<ProviderProductDTO> list = new ArrayList<>();
            for (JsonNode item : productsNode) {
                String title = extractField(item, "product_title", "title", "name", "product_name", "productTitle", "productName", "item_title", "item_name");
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }

                String rawId = extractField(item, "product_id", "pid", "id", "fsn", "productId", "listing_id", "itemId");
                String productUrl = extractField(item, "product_url", "url", "link", "product_link", "productUrl", "web_url");
                if (productUrl == null || productUrl.trim().isEmpty()) {
                    if (rawId != null && !rawId.trim().isEmpty()) {
                        productUrl = "https://www.flipkart.com/item/p/itm?pid=" + rawId.trim();
                    }
                }

                String photoUrl = extractField(item, "product_photo", "product_image", "thumbnail", "image", "img", "productPhoto", "productImage", "imageUrl");
                if (photoUrl == null || photoUrl.trim().isEmpty()) {
                    JsonNode photosNode = item.path("product_photos");
                    if (photosNode.isArray() && !photosNode.isEmpty()) {
                        photoUrl = photosNode.get(0).asText(null);
                    } else if (item.path("images").isArray() && !item.path("images").isEmpty()) {
                        photoUrl = item.path("images").get(0).asText(null);
                    }
                }

                Double price = extractPriceValue(item);
                if (price == null || price <= 0) {
                    continue; // Preserve real prices only - never fabricate or return non-priced items
                }

                String currency = extractField(item, "currency");
                if (currency == null || currency.trim().isEmpty()) {
                    currency = "INR";
                }

                Double rating = parseRating(extractField(item, "product_rating", "rating", "product_star_rating"));
                String storeProductId = (rawId != null && !rawId.trim().isEmpty()) ? rawId.trim() : "FK-" + Math.abs(title.hashCode());
                String brand = extractBrand(title);
                String canonical = extractCanonicalName(title);

                ProviderProductDTO dto = new ProviderProductDTO(
                        "FLIPKART",
                        storeProductId,
                        title.trim(),
                        canonical,
                        brand,
                        "",
                        "Electronics & Consumer Goods",
                        "Flipkart Real-Time Listing: " + title.trim(),
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
            log.error("Failed to parse Real-Time Flipkart Data API search response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public static Double extractPriceValue(JsonNode item) {
        if (item == null) return null;

        String[] priceFields = new String[]{
                "product_price", "price", "current_price", "special_price",
                "product_minimum_offer_price", "selling_price", "sellingPrice",
                "discounted_price", "discountedPrice", "final_price", "offer_price",
                "mrp", "amount", "value", "val", "cost"
        };
        for (String field : priceFields) {
            JsonNode fn = item.path(field);
            if (!fn.isMissingNode() && !fn.isNull()) {
                if (fn.isNumber()) {
                    double val = fn.asDouble();
                    if (val > 0) return Math.round(val * 100.0) / 100.0;
                } else if (fn.isTextual()) {
                    Double parsed = parsePrice(fn.asText());
                    if (parsed != null && parsed > 0) return parsed;
                }
            }
        }

        JsonNode priceObj = item.path("price");
        if (priceObj.isObject()) {
            Double nested = extractPriceValue(priceObj);
            if (nested != null && nested > 0) return nested;
        }
        JsonNode pricingObj = item.path("pricing");
        if (pricingObj.isObject()) {
            Double nested = extractPriceValue(pricingObj);
            if (nested != null && nested > 0) return nested;
        }

        return null;
    }

    private static String safeErrorSummary(String body) {
        if (body == null || body.trim().isEmpty()) return "empty body";
        String clean = body.replaceAll("(?i)key=[^&\\s]+", "key=REDACTED")
                .replaceAll("(?i)\"([^\"]*key[^\"]*)\"\\s*:\\s*\"[^\"]+\"", "\"$1\":\"REDACTED\"");
        return clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
    }

    private static String extractField(JsonNode node, String... fieldNames) {
        if (node == null) return null;
        for (String field : fieldNames) {
            JsonNode val = node.path(field);
            if (!val.isMissingNode() && !val.isNull()) {
                String text = val.asText(null);
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    public static Double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return null;
        }

        String s = priceStr.replace("&nbsp;", " ").replace("\u00a0", " ").trim();
        // Remove currency words and prefixes like "Rs.", "INR", "₹", "USD", etc.
        s = s.replaceAll("(?i)\\b(rs|inr|usd|eur|gbp)\\.?\\s*", "").trim();
        s = s.replaceAll("[^0-9.,]", "").trim();
        if (s.isEmpty()) {
            return null;
        }

        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');

        if (lastDot > lastComma) {
            // e.g. "1,29,990.00" or "1099.99"
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

    public static String extractPid(String storeProductId, String productUrl) {
        if (storeProductId != null && !storeProductId.trim().isEmpty()) {
            String clean = storeProductId.trim();
            if (clean.matches("^[A-Za-z0-9_-]{10,}$")) {
                return clean;
            }
        }
        if (productUrl != null && !productUrl.trim().isEmpty()) {
            Pattern p = Pattern.compile("[?&]pid=([A-Za-z0-9_-]+)");
            Matcher m = p.matcher(productUrl);
            if (m.find()) {
                return m.group(1);
            }
            Pattern p2 = Pattern.compile("/p/([A-Za-z0-9_-]+)");
            Matcher m2 = p2.matcher(productUrl);
            if (m2.find()) {
                return m2.group(1);
            }
        }
        return storeProductId;
    }

    public static String extractBrand(String title) {
        if (title == null || title.trim().isEmpty()) return "Flipkart";
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
        return "Flipkart";
    }

    public static String extractCanonicalName(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Flipkart Product";
        }
        return title.trim().replaceAll("[\\.\"]+$", "");
    }

    public void clearCache() {
        searchCache.clear();
    }

    private String resolveApiKey() {
        String envKey = System.getenv("FLIPKART_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        if (flipkartApiKey != null && !flipkartApiKey.trim().isEmpty()) {
            return flipkartApiKey.trim();
        }
        return null;
    }

    private String resolveApiHost() {
        String envHost = System.getenv("FLIPKART_API_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return envHost.trim();
        }
        if (flipkartApiHost != null && !flipkartApiHost.trim().isEmpty()) {
            return flipkartApiHost.trim();
        }
        return DEFAULT_RAPIDAPI_HOST;
    }

    public Map<String, Object> diagnoseProvider(String query) {
        String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : "Samsung Galaxy S24";
        String host = resolveApiHost();
        boolean configured = isConfigured();

        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("store", "FLIPKART");
        diag.put("host", host);
        diag.put("configured", configured);
        diag.put("activeSearchPath", getActiveSearchPath());
        diag.put("lastError", lastError);
        diag.put("status", getStoreStatus());
        diag.put("query", safeQuery);

        if (!configured) {
            diag.put("message", "Provider is not configured with FLIPKART_API_KEY");
            return diag;
        }

        List<Map<String, Object>> attempts = new ArrayList<>();
        String workingPath = null;
        List<ProviderProductDTO> workingProducts = null;

        for (String candidate : CANDIDATE_SEARCH_PATHS) {
            Map<String, Object> att = new LinkedHashMap<>();
            att.put("path", candidate);
            try {
                long start = System.currentTimeMillis();
                String response = executeSearchRequest(host, candidate, safeQuery);
                long elapsed = System.currentTimeMillis() - start;
                att.put("status", 200);
                att.put("elapsedMs", elapsed);
                att.put("responseLength", response != null ? response.length() : 0);
                att.put("bodySnippet", response != null ? safeSnippet(response, 300) : null);

                List<ProviderProductDTO> parsed = parseSearchResponse(response);
                att.put("parsedCount", parsed.size());

                if (!parsed.isEmpty() && workingPath == null) {
                    workingPath = candidate;
                    workingProducts = parsed;
                }
            } catch (RestClientResponseException e) {
                att.put("status", e.getStatusCode().value());
                att.put("error", safeErrorSummary(e.getResponseBodyAsString()));
            } catch (Exception e) {
                att.put("status", "ERROR");
                att.put("error", e.getMessage());
            }
            attempts.add(att);
            if (workingPath != null) {
                break; // Discovered active working endpoint
            }
        }

        diag.put("endpointAttempts", attempts);
        if (workingPath != null) {
            this.discoveredSearchPath = workingPath;
            this.isUnavailable = false;
            this.lastError = null;
            diag.put("discoveredWorkingPath", workingPath);
            diag.put("parsedProductCount", workingProducts.size());
            diag.put("sampleProducts", workingProducts.stream().limit(3).toList());
        } else {
            diag.put("discoveredWorkingPath", null);
        }

        return diag;
    }

    private static String safeSnippet(String text, int maxLen) {
        if (text == null) return null;
        String clean = text.replaceAll("(?i)key=[^&\\s]+", "key=REDACTED")
                .replaceAll("(?i)\"([^\"]*key[^\"]*)\"\\s*:\\s*\"[^\"]+\"", "\"$1\":\"REDACTED\"");
        return clean.length() > maxLen ? clean.substring(0, maxLen) + "..." : clean;
    }

    private JsonNode findFirstProductArray(JsonNode node) {
        if (node == null) return null;
        if (node.isArray() && !node.isEmpty()) {
            JsonNode first = node.get(0);
            if (first.isObject() && hasProductField(first)) {
                return node;
            }
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode child = entry.getValue();
                if (child.isArray() && !child.isEmpty()) {
                    JsonNode first = child.get(0);
                    if (first.isObject() && hasProductField(first)) {
                        return child;
                    }
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                JsonNode inner = findFirstProductArray(fields.next().getValue());
                if (inner != null) return inner;
            }
        }
        return null;
    }

    private static boolean hasProductField(JsonNode item) {
        return item.has("product_title") || item.has("title") || item.has("name") ||
                item.has("product_name") || item.has("productTitle") || item.has("productName") ||
                item.has("price") || item.has("current_price") || item.has("product_price") ||
                item.has("selling_price") || item.has("product_id") || item.has("pid") || item.has("id");
    }
}
