package com.pricewise.backend.provider;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.dto.ProviderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Service
public class ProviderManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ProviderManager.class);
    private static final int DEFAULT_SEARCH_TIMEOUT_SECONDS = 7;

    private final List<PriceProvider> providers;
    private final ExecutorService executorService;
    private final int searchTimeoutSeconds;

    @org.springframework.beans.factory.annotation.Autowired
    public ProviderManager(List<PriceProvider> providers) {
        this(providers, Executors.newFixedThreadPool(
                Math.max(4, providers != null ? providers.size() : 4),
                r -> {
                    Thread t = new Thread(r, "provider-manager-pool");
                    t.setDaemon(true);
                    return t;
                }
        ), DEFAULT_SEARCH_TIMEOUT_SECONDS);
    }

    public ProviderManager(List<PriceProvider> providers, ExecutorService executorService) {
        this(providers, executorService, DEFAULT_SEARCH_TIMEOUT_SECONDS);
    }

    public ProviderManager(List<PriceProvider> providers, ExecutorService executorService, int searchTimeoutSeconds) {
        this.providers = providers != null ? providers : Collections.emptyList();
        this.executorService = executorService;
        this.searchTimeoutSeconds = searchTimeoutSeconds > 0 ? searchTimeoutSeconds : DEFAULT_SEARCH_TIMEOUT_SECONDS;
        log.info("Initialized ProviderManager with {} providers (concurrent execution enabled, timeout: {}s)",
                this.providers.size(), this.searchTimeoutSeconds);
        for (PriceProvider p : this.providers) {
            log.info(" - Provider: {} | Status: {} | Configured: {}", p.getStoreName(), p.getStoreStatus(), p.isConfigured());
        }
    }

    @Override
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    public List<ProviderStatusDTO> getProviderStatuses() {
        List<ProviderStatusDTO> statuses = new ArrayList<>();
        for (PriceProvider p : providers) {
            statuses.add(new ProviderStatusDTO(
                    p.getStoreName(),
                    p.isConfigured(),
                    p.getStoreStatus(),
                    p.getDescription(),
                    p.getRequiredConfig()
            ));
        }
        return statuses;
    }

    public List<ProviderProductDTO> searchAll(String query) {
        if (providers.isEmpty()) {
            return Collections.emptyList();
        }

        // Dispatch all provider searches concurrently
        List<CompletableFuture<List<ProviderProductDTO>>> futures = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.debug("Invoking provider [{}] concurrently for query: {}", provider.getStoreName(), query);
                        List<ProviderProductDTO> results = provider.searchProducts(query);
                        return results != null ? results : Collections.<ProviderProductDTO>emptyList();
                    } catch (Exception e) {
                        // Non-fatal: isolate provider errors so other providers succeed
                        log.error("Provider [{}] encountered an error during search: {}", provider.getStoreName(), e.getMessage());
                        return Collections.<ProviderProductDTO>emptyList();
                    }
                }, executorService))
                .toList();

        // Wait for all concurrent searches to finish within the bounded timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(searchTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Concurrent provider search for query '{}' reached overall timeout of {}s. Collecting completed provider results.",
                    query, searchTimeoutSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Concurrent provider search for query '{}' was interrupted.", query);
        } catch (ExecutionException e) {
            log.error("Execution exception during concurrent provider search for '{}': {}", query, e.getMessage());
        }

        // Aggregate results in deterministic provider order
        List<ProviderProductDTO> aggregated = new ArrayList<>();
        for (CompletableFuture<List<ProviderProductDTO>> future : futures) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    List<ProviderProductDTO> results = future.get();
                    if (results != null && !results.isEmpty()) {
                        aggregated.addAll(results);
                    }
                } catch (Exception e) {
                    log.error("Error retrieving completed provider search results: {}", e.getMessage());
                }
            } else if (!future.isDone()) {
                future.cancel(true);
            }
        }

        return aggregated;
    }

    public PriceProvider getProvider(String storeName) {
        for (PriceProvider p : providers) {
            if (p.getStoreName().equalsIgnoreCase(storeName)) {
                return p;
            }
        }
        return null;
    }
}

