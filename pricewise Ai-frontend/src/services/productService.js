import api from "./api";

export const productService = {
  // Search products across all connected providers
  searchProducts: (query = "") => {
    return api.get("/products/search", {
      params: { query },
    });
  },

  // Get full product comparison details
  getProductDetails: (id) => {
    return api.get(`/products/${id}`);
  },

  // Get historical price points and trends for Recharts graph
  getProductHistory: (id) => {
    return api.get(`/products/${id}/history`);
  },

  // Get connected store provider statuses
  getProviders: () => {
    return api.get("/products/providers");
  },
};

export default productService;
