package com.pricewise.backend.provider;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.dto.ProviderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProviderManager {

    private static final Logger log = LoggerFactory.getLogger(ProviderManager.class);

    private final List<PriceProvider> providers;

    public ProviderManager(List<PriceProvider> providers) {
        this.providers = providers;
        log.info("Initialized ProviderManager with {} providers", providers.size());
        for (PriceProvider p : providers) {
            log.info(" - Provider: {} | Status: {} | Configured: {}", p.getStoreName(), p.getStoreStatus(), p.isConfigured());
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
        List<ProviderProductDTO> aggregated = new ArrayList<>();

        for (PriceProvider provider : providers) {
            try {
                log.debug("Invoking provider [{}] for query: {}", provider.getStoreName(), query);
                List<ProviderProductDTO> results = provider.searchProducts(query);
                if (results != null && !results.isEmpty()) {
                    aggregated.addAll(results);
                }
            } catch (Exception e) {
                // Non-fatal: isolate provider errors so other providers succeed
                log.error("Provider [{}] encountered an error during search: {}", provider.getStoreName(), e.getMessage());
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
