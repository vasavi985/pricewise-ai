import api from "./api";

export const trackingService = {
  // Get all active tracked products for authenticated user
  getTrackedProducts: () => {
    return api.get("/tracking");
  },

  // Start tracking a product listing
  trackProduct: (data) => {
    return api.post("/tracking", data);
  },

  // Stop tracking
  stopTracking: (id) => {
    return api.delete(`/tracking/${id}`);
  },

  // Trigger an immediate manual price check
  checkPriceNow: (id) => {
    return api.post(`/tracking/${id}/check-now`);
  },
};

export default trackingService;
