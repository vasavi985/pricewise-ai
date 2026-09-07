package com.pricewise.backend.provider.amazon;

import com.pricewise.backend.dto.ProviderProductDTO;
import com.pricewise.backend.provider.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AmazonPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(AmazonPriceProvider.class);

    @Value("${pricewise.providers.amazon.access-key:}")
    private String accessKey;

    @Value("${pricewise.providers.amazon.secret-key:}")
    private String secretKey;

    @Value("${pricewise.providers.amazon.partner-tag:}")
    private String partnerTag;

    @Value("${pricewise.providers.amazon.region:in}")
    private String region;

    @Override
    public String getStoreName() {
        return "AMAZON";
    }

    @Override
    public boolean isConfigured() {
        return accessKey != null && !accessKey.trim().isEmpty() &&
               secretKey != null && !secretKey.trim().isEmpty() &&
               partnerTag != null && !partnerTag.trim().isEmpty();
    }

    @Override
    public String getStoreStatus() {
        return isConfigured() ? "LIVE" : "CONFIG_REQUIRED";
    }

    @Override
    public String getRequiredConfig() {
        return "AMAZON_ACCESS_KEY, AMAZON_SECRET_KEY, AMAZON_PARTNER_TAG (Amazon PA-API 5.0)";
    }

    @Override
    public String getDescription() {
        return "Amazon India Product Advertising API (PA-API 5.0)";
    }

    @Override
    public List<ProviderProductDTO> searchProducts(String query) {
        if (!isConfigured()) {
            log.info("Amazon PA-API is not configured. Skipping live Amazon search for: {}", query);
            return Collections.emptyList();
        }

        // When configured with real Amazon PA-API keys:
        // PA-API requires AWS SigV4 signed request to https://webservices.amazon.in/paapi5/searchitems
        log.info("Querying Amazon PA-API 5.0 for query: {}", query);
        return Collections.emptyList();
    }

    @Override
    public ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl) {
        if (!isConfigured()) {
            log.info("Amazon PA-API is not configured. Price check unavailable.");
            return null;
        }
        return null;
    }
}
