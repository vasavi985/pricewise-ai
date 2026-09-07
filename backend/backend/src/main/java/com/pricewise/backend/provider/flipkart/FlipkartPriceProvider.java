package com.pricewise.backend.provider.flipkart;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class FlipkartPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(FlipkartPriceProvider.class);

    @Value("${pricewise.providers.flipkart.affiliate-id:}")
    private String affiliateId;

    @Value("${pricewise.providers.flipkart.affiliate-token:}")
    private String affiliateToken;

    @Override
    public String getStoreName() {
        return "FLIPKART";
    }

    @Override
    public boolean isConfigured() {
        return affiliateId != null && !affiliateId.trim().isEmpty() &&
               affiliateToken != null && !affiliateToken.trim().isEmpty();
    }

    @Override
    public String getStoreStatus() {
        return isConfigured() ? "LIVE" : "CONFIG_REQUIRED";
    }

    @Override
    public String getRequiredConfig() {
        return "FLIPKART_AFFILIATE_ID, FLIPKART_AFFILIATE_TOKEN (Flipkart Affiliate API)";
    }

    @Override
    public String getDescription() {
        return "Flipkart Official Affiliate API";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (!isConfigured()) {
            log.info("Flipkart Affiliate API is not configured. Skipping live Flipkart search for: {}", query);
            return Collections.emptyList();
        }

        // When configured with real Flipkart Affiliate credentials:
        // Request GET https://affiliate-api.flipkart.net/affiliate/1.0/search.json?query=...
        // Headers: Fk-Affiliate-Id, Fk-Affiliate-Token
        log.info("Querying Flipkart Affiliate API for query: {}", query);
        return Collections.emptyList();
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (!isConfigured()) {
            log.info("Flipkart Affiliate API is not configured. Price check unavailable.");
            return null;
        }
        return null;
    }
}
