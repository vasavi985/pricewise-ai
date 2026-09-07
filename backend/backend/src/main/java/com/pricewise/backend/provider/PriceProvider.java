package com.pricewise.backend.provider;

import com.pricewise.backend.dto.ProviderProductDTO;
import java.util.List;

public interface PriceProvider {

    String getStoreName();

    boolean isConfigured();

    String getStoreStatus(); // LIVE, SAMPLE_DATA, CONFIG_REQUIRED, UNAVAILABLE

    String getRequiredConfig();

    String getDescription();

    List<ProviderProductDTO> searchProducts(String query);

    ProviderProductDTO fetchCurrentPrice(String storeProductId, String productUrl);
}
