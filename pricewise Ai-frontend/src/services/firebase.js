import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "pricewise-ai-be26e.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "pricewise-ai-be26e",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "pricewise-ai-be26e.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "79377350254",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:79377350254:web:bebf6d70228d2a89e1b07f",
};

// Initialize Firebase client app once
const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);

export default app;
