import React, { useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import productService from "./services/productService";

import Home from "./pages/Home";
import Results from "./pages/Results";
import ProductDetails from "./pages/ProductDetails";
import Tracking from "./pages/Tracking";
import History from "./pages/History";
import About from "./pages/About";
import Login from "./pages/Login";
import Register from "./pages/Register";

function App() {
  useEffect(() => {
    // Proactive background warmup ping to pre-warm backend (Render cold-start) on initial app visit
    productService.getProviders().catch(() => {});
  }, []);

  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/results" element={<Results />} />
          <Route path="/product/:id" element={<ProductDetails />} />
          <Route path="/tracking" element={<Tracking />} />
          <Route path="/history" element={<History />} />
          <Route path="/about" element={<About />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;