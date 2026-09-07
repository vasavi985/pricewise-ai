import api from "./api";

export const historyService = {
  // Fetch authenticated user's isolated search history
  getUserHistory: () => {
    return api.get("/history");
  },

  // Clear all search history records for the authenticated user
  clearUserHistory: () => {
    return api.delete("/history");
  },
};

export default historyService;
