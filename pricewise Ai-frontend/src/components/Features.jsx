import React from "react";
import { FaChartLine, FaBell, FaStore, FaShieldAlt } from "react-icons/fa";
import "../styles/features.css";

const FEATURES_LIST = [
  {
    icon: <FaStore className="feature-icon store-ico" />,
    title: "Multi-Store Comparison",
    description: "Compare prices side-by-side across Amazon, Flipkart, Croma, and live commerce. See stock availability and exact savings instantly.",
  },
  {
    icon: <FaBell className="feature-icon bell-ico" />,
    title: "Automated Price Drop Alerts",
    description: "Set a target price or desired discount percentage. Our background scheduler monitors prices and notifies you the second they drop.",
  },
  {
    icon: <FaChartLine className="feature-icon chart-ico" />,
    title: "Real Historical Price Trends",
    description: "View genuine historical price graphs powered by immutable records. Distinguish all-time lows from temporary markups.",
  },
  {
    icon: <FaShieldAlt className="feature-icon shield-ico" />,
    title: "Verified Data & Transparency",
    description: "Clear badges show whether data is live, catalog-verified, or requires configuration. No fake prices or artificial discounts.",
  },
];

function Features() {
  return (
    <section className="features-section" id="features">
      <div className="features-header">
        <span className="features-badge">Built For Smart Shoppers</span>
        <h2 className="features-title">Why Use PriceWise AI?</h2>
        <p className="features-desc">
          Stop opening 10 browser tabs to find the best deal. PriceWise AI gives you live intelligence, historical context, and automated tracking.
        </p>
      </div>

      <div className="features-grid">
        {FEATURES_LIST.map((feat, idx) => (
          <div key={idx} className="feature-card">
            <div className="icon-wrapper">{feat.icon}</div>
            <h3 className="feature-card-title">{feat.title}</h3>
            <p className="feature-card-desc">{feat.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export default Features;
