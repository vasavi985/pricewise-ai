import React from "react";
import { FaBolt, FaCheckCircle, FaExclamationTriangle, FaHourglassHalf, FaArrowDown } from "react-icons/fa";
import "../styles/recommendationcard.css";

function Recommendationcard({ product }) {
  if (!product || !product.lowestPrice) return null;

  const {
    lowestPrice,
    highestPrice,
    savingsAmount,
    savingsPercentage,
    bestStore,
    trendSummary,
    recommendation,
  } = product;

  const getRecommendationBadge = () => {
    switch (recommendation) {
      case "BUY NOW":
        return {
          icon: <FaBolt />,
          label: "Strong Buy — Great Price",
          className: "rec-buy-now",
        };
      case "GOOD PRICE":
        return {
          icon: <FaCheckCircle />,
          label: "Good Price — Fair Value",
          className: "rec-good",
        };
      case "WAIT FOR LOWER PRICE":
        return {
          icon: <FaHourglassHalf />,
          label: "Consider Waiting — Price Elevated",
          className: "rec-wait",
        };
      default:
        return {
          icon: <FaExclamationTriangle />,
          label: "Tracking Active",
          className: "rec-neutral",
        };
    }
  };

  const badge = getRecommendationBadge();

  return (
    <div className="recommendation-card">
      <div className="rec-header">
        <div className="rec-title-group">
          <span className="rec-ai-badge">✨ Price Intelligence</span>
          <h3 className="rec-headline">Best Available Deal</h3>
        </div>

        <div className={`rec-status-pill ${badge.className}`}>
          {badge.icon} {badge.label}
        </div>
      </div>

      <div className="rec-body">
        <div className="rec-price-stat">
          <span className="rec-store-label">Lowest Price at {bestStore}:</span>
          <div className="rec-price-val">
            <span className="rec-cur">₹</span>
            {lowestPrice.toLocaleString("en-IN")}
          </div>
        </div>

        {savingsAmount > 0 && (
          <div className="rec-savings-box">
            <div className="savings-highlight">
              <FaArrowDown /> ₹{savingsAmount.toLocaleString("en-IN")} cheaper ({savingsPercentage}%)
            </div>
            <span className="savings-subtext">Compared to highest store listing</span>
          </div>
        )}
      </div>

      {trendSummary && (
        <div className="rec-analysis">
          <p className="rec-text">{trendSummary}</p>
        </div>
      )}
    </div>
  );
}

export default Recommendationcard;