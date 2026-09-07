package com.pricewise.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ProductSearchResultDTO {
    private String query;
    private int totalFound;
    private List<ProviderStatusDTO> providers = new ArrayList<>();
    private List<PriceComparisonDTO> results = new ArrayList<>();

    public ProductSearchResultDTO() {
    }

    public ProductSearchResultDTO(String query, List<PriceComparisonDTO> results, List<ProviderStatusDTO> providers) {
        this.query = query;
        this.results = results != null ? results : new ArrayList<>();
        this.totalFound = this.results.size();
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTotalFound() {
        return totalFound;
    }

    public void setTotalFound(int totalFound) {
        this.totalFound = totalFound;
    }

    public List<ProviderStatusDTO> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderStatusDTO> providers) {
        this.providers = providers;
    }

    public List<PriceComparisonDTO> getResults() {
        return results;
    }

    public void setResults(List<PriceComparisonDTO> results) {
        this.results = results;
        this.totalFound = results != null ? results.size() : 0;
    }
}
