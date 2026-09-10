import React from "react";
import "../styles/StoreLogos.css";

const STORES = [
  { name: "Amazon", tag: "Real-Time Search", badge: "Live Products & Pricing", color: "#FF9900", icon: "🛒" },
  { name: "Flipkart", tag: "Real-Time Search", badge: "Live Products & Pricing", color: "#2874F0", icon: "⚡" },
];

function StoreLogos() {
  return (
    <section className="stores-section">
      <div className="stores-header">
        <span className="stores-subtitle">Real Price Intelligence</span>
        <h3 className="stores-title">Amazon vs Flipkart Price Comparison</h3>
        <p className="stores-desc">
          PriceWise AI queries live products and real prices simultaneously from Amazon and Flipkart so you can see which store has the lowest price instantly.
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