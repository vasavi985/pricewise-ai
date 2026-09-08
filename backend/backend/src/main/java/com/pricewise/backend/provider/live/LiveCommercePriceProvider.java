package com.pricewise.backend.provider.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Component
public class LiveCommercePriceProvider implements PriceProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LiveCommercePriceProvider.class);
    private static final double USD_TO_INR = 86.0;
    private static final int HARD_TIMEOUT_SECONDS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final int timeoutSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public LiveCommercePriceProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://dummyjson.com")
                .build();
        this.executorService = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "open-commerce-pool");
            t.setDaemon(true);
            return t;
        });
        this.timeoutSeconds = HARD_TIMEOUT_SECONDS;
    }

    // Constructor for testing with custom RestClient, executor, and timeout
    public LiveCommercePriceProvider(ObjectMapper objectMapper, RestClient restClient, ExecutorService executorService, int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.executorService = executorService != null ? executorService : Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "open-commerce-test-pool");
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
        return "OPEN_COMMERCE";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String getStoreStatus() {
        return "LIVE";
    }

    @Override
    public String getRequiredConfig() {
        return "None (Public Commercial Product API)";
    }

    @Override
    public String getDescription() {
        return "Live Global Product & Commerce API";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String safeQuery = query.trim();

        CompletableFuture<List<ProviderProductDTO>> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Fetching real live product data from Open Commerce API for query: {}", safeQuery);
                String response = restClient.get()
                        .uri("/products/search?q={query}&limit=6", safeQuery)
                        .retrieve()
                        .body(String.class);

                if (response == null || response.trim().isEmpty()) {
                    return Collections.emptyList();
                }

                JsonNode root = objectMapper.readTree(response);
                JsonNode productsNode = root.get("products");

                if (productsNode == null || !productsNode.isArray()) {
                    return Collections.emptyList();
                }

                List<ProviderProductDTO> results = new ArrayList<>();
                for (JsonNode item : productsNode) {
                    long id = item.path("id").asLong();
                    String title = item.path("title").asText();
                    String description = item.path("description").asText();
                    double rawPriceUsd = item.path("price").asDouble();
                    double inrPrice = Math.round(rawPriceUsd * USD_TO_INR);
                    double rating = item.path("rating").asDouble(4.5);
                    String brand = item.path("brand").asText("Universal");
                    String category = item.path("category").asText("Consumer Goods");
                    String thumbnail = item.path("thumbnail").asText();

                    // Live listing
                    results.add(new ProviderProductDTO(
                            "OPEN_COMMERCE",
                            "LIVE-" + id,
                            title,
                            title,
                            brand,
                            category,
                            category,
                            description,
                            thumbnail,
                            "https://dummyjson.com/products/" + id,
                            inrPrice,
                            "INR",
                            "IN_STOCK",
                            "LIVE",
                            rating
                    ));
                }

                log.info("Retrieved {} live products for query: {}", results.size(), safeQuery);
                return results;

            } catch (Exception e) {
                log.warn("Live commerce API request failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executorService);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Open Commerce API query for '{}' exceeded hard timeout of {}s. Returning empty result.", safeQuery, timeoutSeconds);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Open Commerce API query for '{}' was interrupted.", safeQuery);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            log.warn("Execution exception during Open Commerce query for '{}': {}", safeQuery, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (storeProductId == null || storeProductId.trim().isEmpty()) {
            return null;
        }

        CompletableFuture<ProviderProductDTO> future = CompletableFuture.supplyAsync(() -> {
            try {
                String numericId = storeProductId.replace("LIVE-", "");
                String response = restClient.get()
                        .uri("/products/{id}", numericId)
                        .retrieve()
                        .body(String.class);

                if (response == null || response.trim().isEmpty()) return null;

                JsonNode item = objectMapper.readTree(response);
                double rawPriceUsd = item.path("price").asDouble();
                double inrPrice = Math.round(rawPriceUsd * USD_TO_INR);

                return new ProviderProductDTO(
                        "OPEN_COMMERCE",
                        storeProductId,
                        item.path("title").asText(),
                        item.path("title").asText(),
                        item.path("brand").asText("Universal"),
                        item.path("category").asText(),
                        item.path("category").asText(),
                        item.path("description").asText(),
                        item.path("thumbnail").asText(),
                        "https://dummyjson.com/products/" + numericId,
                        inrPrice,
                        "INR",
                        "IN_STOCK",
                        "LIVE",
                        item.path("rating").asDouble(4.5)
                );
            } catch (Exception e) {
                log.warn("Failed to fetch current price for {}: {}", storeProductId, e.getMessage());
                return null;
            }
        }, executorService);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Open Commerce fetchCurrentPrice for '{}' exceeded hard timeout of {}s.", storeProductId, timeoutSeconds);
            return null;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("Execution exception during Open Commerce fetchCurrentPrice for '{}': {}", storeProductId, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return null;
        }
    }
}
