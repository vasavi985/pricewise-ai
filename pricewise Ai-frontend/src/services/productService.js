import api from "./api";

const CACHE_TTL_MS = 3 * 60 * 1000; // 3 minutes
const PROVIDERS_TTL_MS = 10 * 60 * 1000; // 10 minutes

const searchCache = new Map();
const detailsCache = new Map();
const historyCache = new Map();
let providersCache = null;

export const productService = {
  // Search products across all connected providers with memory cache
  searchProducts: async (query = "", forceRefresh = false) => {
    const key = (query || "").trim().toLowerCase();
    const now = Date.now();

    if (!forceRefresh && searchCache.has(key)) {
      const entry = searchCache.get(key);
      if (now - entry.timestamp < CACHE_TTL_MS) {
        return entry.data;
      }
    }

    const data = await api.get("/products/search", {
      params: { query },
    });

    searchCache.set(key, { data, timestamp: now });
    return data;
  },

  // Get full product comparison details with memory cache
  getProductDetails: async (id, forceRefresh = false) => {
    const key = String(id);
    const now = Date.now();

    if (!forceRefresh && detailsCache.has(key)) {
      const entry = detailsCache.get(key);
      if (now - entry.timestamp < CACHE_TTL_MS) {
        return entry.data;
      }
    }

    const data = await api.get(`/products/${id}`);
    detailsCache.set(key, { data, timestamp: now });
    return data;
  },

  // Get historical price points and trends for Recharts graph with memory cache
  getProductHistory: async (id, forceRefresh = false) => {
    const key = String(id);
    const now = Date.now();

    if (!forceRefresh && historyCache.has(key)) {
      const entry = historyCache.get(key);
      if (now - entry.timestamp < CACHE_TTL_MS) {
        return entry.data;
      }
    }

    const data = await api.get(`/products/${id}/history`);
    historyCache.set(key, { data, timestamp: now });
    return data;
  },

  // Get connected store provider statuses with memory cache
  getProviders: async (forceRefresh = false) => {
    const now = Date.now();

    if (!forceRefresh && providersCache && now - providersCache.timestamp < PROVIDERS_TTL_MS) {
      return providersCache.data;
    }

    const data = await api.get("/products/providers");
    providersCache = { data, timestamp: now };
    return data;
  },

  // Clear all caches when desired (e.g. manual refresh)
  clearCache: () => {
    searchCache.clear();
    detailsCache.clear();
    historyCache.clear();
    providersCache = null;
  },
};

export default productService;
