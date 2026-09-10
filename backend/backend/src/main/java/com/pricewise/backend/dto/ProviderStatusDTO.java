package com.pricewise.backend.dto;

public class ProviderStatusDTO {
    private String store;
    private boolean configured;
    private String status; // LIVE, SAMPLE_DATA, CONFIG_REQUIRED, UNAVAILABLE
    private String description;
    private String requiredConfig;
    private String error;

    public ProviderStatusDTO() {
    }

    public ProviderStatusDTO(String store, boolean configured, String status, String description, String requiredConfig) {
        this(store, configured, status, description, requiredConfig, null);
    }

    public ProviderStatusDTO(String store, boolean configured, String status, String description, String requiredConfig, String error) {
        this.store = store;
        this.configured = configured;
        this.status = status;
        this.description = description;
        this.requiredConfig = requiredConfig;
        this.error = error;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequiredConfig() {
        return requiredConfig;
    }

    public void setRequiredConfig(String requiredConfig) {
        this.requiredConfig = requiredConfig;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
