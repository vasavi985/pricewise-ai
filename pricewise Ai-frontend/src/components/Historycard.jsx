import React, { useState } from "react";
import { Link } from "react-router-dom";
import { FaClock, FaArrowDown, FaSync, FaTrashAlt, FaExternalLinkAlt } from "react-icons/fa";
import "../styles/historycard.css";

function Historycard({ item, onStopTracking, onCheckNow }) {
  const [isChecking, setIsChecking] = useState(false);

  if (!item) return null;

  const {
    id,
    productId,
    productName,
    store,
    imageUrl,
    productUrl,
    currentPrice,
    initialPrice,
    targetPrice,
    priceDrop,
    dropPercentage,
    lastCheckedAt,
    userEmail,
  } = item;

  const handleCheck = async () => {
    setIsChecking(true);
    try {
      if (onCheckNow) await onCheckNow(id);
    } finally {
      setIsChecking(false);
    }
  };

  const formatTimeAgo = (dateStr) => {
    if (!dateStr) return "Just now";
    try {
      const date = new Date(dateStr);
      const now = new Date();
      const diffMin = Math.round((now - date) / (1000 * 60));
      if (diffMin < 1) return "Just now";
      if (diffMin < 60) return `${diffMin}m ago`;
      const diffHours = Math.round(diffMin / 60);
      if (diffHours < 24) return `${diffHours}h ago`;
      return `${Math.round(diffHours / 24)}d ago`;
    } catch {
      return "Recently";
    }
  };

  const hasDropped = priceDrop && priceDrop > 0;

  return (
    <div className={`history-card ${hasDropped ? "card-dropped" : ""}`}>
      {hasDropped && (
        <div className="drop-banner">
          <FaArrowDown /> Price dropped by ₹{priceDrop.toLocaleString("en-IN")} ({dropPercentage}%)!
        </div>
      )}

      <div className="history-main">
        <img
          src={imageUrl || "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80"}
          alt={productName}
          className="history-img"
          onError={(e) => {
            e.target.src = "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
          }}
        />

        <div className="history-details">
          <div className="history-meta">
            <span className={`history-store-tag ${store === "CATALOG" ? "tag-catalog" : ""}`}>
              {store === "CATALOG" ? "Catalog Benchmark" : store}
            </span>
            <span className="history-time"><FaClock /> Checked {formatTimeAgo(lastCheckedAt)}</span>
          </div>

          <h4 className="history-title">
            <Link to={`/product/${productId}`}>{productName}</Link>
          </h4>

          <div className="history-price-grid">
            <div className="price-stat-box">
              <span className="stat-lbl">{store === "CATALOG" ? "Benchmark Price" : "Current Price"}</span>
              <span className="stat-val current">
                {currentPrice ? `₹${currentPrice.toLocaleString("en-IN")}` : "N/A"}
              </span>
            </div>

            <div className="price-stat-box">
              <span className="stat-lbl">Target Price</span>
              <span className="stat-val target">
                {targetPrice ? `₹${targetPrice.toLocaleString("en-IN")}` : "Any Drop"}
              </span>
            </div>

            <div className="price-stat-box">
              <span className="stat-lbl">Initial Tracked</span>
              <span className="stat-val initial">
                {initialPrice ? `₹${initialPrice.toLocaleString("en-IN")}` : "N/A"}
              </span>
            </div>
          </div>

          {userEmail && (
            <div className="notify-email-tag">
              Alerts sent to: <strong>{userEmail}</strong>
            </div>
          )}
        </div>
      </div>

      <div className="history-actions">
        <button
          type="button"
          className="action-btn check-now-btn"
          onClick={handleCheck}
          disabled={isChecking}
          title="Verify live price now"
        >
          <FaSync className={isChecking ? "spin-icon" : ""} />
          {isChecking ? "Checking..." : "Check Now"}
        </button>

        {productUrl && (
          <a
            href={productUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="action-btn store-link-btn"
          >
            Visit Store <FaExternalLinkAlt className="ext-ico" />
          </a>
        )}

        <button
          type="button"
          className="action-btn stop-btn"
          onClick={() => onStopTracking(id)}
          title="Stop tracking this price"
        >
          <FaTrashAlt /> Stop Tracking
        </button>
      </div>
    </div>
  );
}

export default Historycard;
