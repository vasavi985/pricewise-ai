import axios from "axios";
import { auth } from "./firebase";

// Automatically ensure /api path suffix is present and normalized
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";
const normalizedBaseUrl = rawBaseUrl.endsWith("/api")
  ? rawBaseUrl
  : `${rawBaseUrl.replace(/\/+$/, "")}/api`;

const api = axios.create({
  baseURL: normalizedBaseUrl,
  timeout: 90000,
  headers: {
    "Content-Type": "application/json",
  },
});

// Automatically inject Firebase ID token if a user is authenticated
api.interceptors.request.use(
  async (config) => {
    try {
      const user = auth.currentUser;
      if (user) {
        const token = await user.getIdToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
      }
    } catch (e) {
      // Non-blocking: continue request even if token extraction fails
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.message ||
      "Unable to connect to PriceWise server. Please ensure backend is running.";
    console.error("API Error:", message);
    return Promise.reject(new Error(message));
  }
);

export default api;
