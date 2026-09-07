import React from "react";
import "../styles/StoreLogos.css";

const STORES = [
  { name: "Amazon", tag: "Supported Store", badge: "PA-API 5.0", color: "#FF9900", icon: "🛒" },
  { name: "Flipkart", tag: "Supported Store", badge: "Affiliate API", color: "#2874F0", icon: "⚡" },
  { name: "Open Commerce", tag: "Live Provider", badge: "Live Products", color: "#6C4CF1", icon: "🌐" },
  { name: "Croma", tag: "Enterprise Store", badge: "Partner Gateway", color: "#00B67A", icon: "📱" },
];

function StoreLogos() {
  return (
    <section className="stores-section">
      <div className="stores-header">
        <span className="stores-subtitle">Multi-Store Price Intelligence</span>
        <h3 className="stores-title">Compare Across India's Top Retailers</h3>
        <p className="stores-desc">
          PriceWise AI continuously queries, normalizes, and compares prices across supported stores so you never overpay.
        </p>
      </div>

      <div className="stores-grid">
        {STORES.map((store) => (
          <div key={store.name} className="store-pill" style={{ "--store-accent": store.color }}>
            <span className="store-icon">{store.icon}</span>
            <div className="store-info">
              <span className="store-name">{store.name}</span>
              <span className="store-badge">{store.badge}</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export default StoreLogos;