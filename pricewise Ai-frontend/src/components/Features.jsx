import React from "react";
import { FaChartLine, FaBell, FaStore, FaShieldAlt } from "react-icons/fa";
import "../styles/features.css";

const FEATURES_LIST = [
  {
    icon: <FaStore className="feature-icon store-ico" />,
    title: "Amazon vs Flipkart Comparison",
    description: "Compare real prices side-by-side between Amazon and Flipkart. Spot the lowest deal, exact price difference, and instant savings.",
  },
  {
    icon: <FaBell className="feature-icon bell-ico" />,
    title: "Automated Price Drop Alerts",
    description: "Set your target price for either store. Our background scheduler monitors live prices and alerts you the second prices drop.",
  },
  {
    icon: <FaChartLine className="feature-icon chart-ico" />,
    title: "Real Historical Price Trends",
    description: "View genuine historical price graphs powered by immutable records. Distinguish genuine all-time lows from temporary promotions.",
  },
  {
    icon: <FaShieldAlt className="feature-icon shield-ico" />,
    title: "100% Real Live Commerce Data",
    description: "Zero mock data, zero fake catalogs, and zero synthetic prices. Every listing links directly to the real product on Amazon or Flipkart.",
  },
];

function Features() {
  return (
    <section className="features-section" id="features">
      <div className="features-header">
        <span className="features-badge">Built For Smart Shoppers</span>
        <h2 className="features-title">Why Use PriceWise AI?</h2>
        <p className="features-desc">
          Stop switching back and forth between Amazon and Flipkart. PriceWise AI queries both in real time and highlights the genuine winner.
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
