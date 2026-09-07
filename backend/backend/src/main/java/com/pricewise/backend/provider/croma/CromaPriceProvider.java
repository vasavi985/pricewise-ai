package com.pricewise.backend.provider.croma;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CromaPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CromaPriceProvider.class);

    @Value("${pricewise.providers.croma.api-key:}")
    private String apiKey;

    @Override
    public String getStoreName() {
        return "CROMA";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String getStoreStatus() {
        return isConfigured() ? "LIVE" : "UNAVAILABLE";
    }

    @Override
    public String getRequiredConfig() {
        return "CROMA_API_KEY (Enterprise/Commercial Partner Agreement)";
    }

    @Override
    public String getDescription() {
        return "Croma Partner / Commerce Gateway";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (!isConfigured()) {
            log.info("Croma has no open public API. Integration status: UNAVAILABLE");
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        return null;
    }
}
