import React from "react";
import { FaExternalLinkAlt, FaCheck, FaTimes, FaClock, FaBell } from "react-icons/fa";
import "../styles/storecard.css";

function Storecard({ storePrice, onTrack }) {
  if (!storePrice) return null;

  const {
    id,
    store,
    title,
    price,
    currency = "INR",
    availability = "IN_STOCK",
    productUrl,
    status = "LIVE",
    lastCheckedAt,
    isLowest,
  } = storePrice;

  const isUnavailable = availability === "UNAVAILABLE" || price == null || price <= 0;
  const isLive = status === "LIVE" && !isUnavailable;
  const storeUpper = (store || "").toUpperCase();

  const getStoreLogoColor = (name) => {
    switch (name) {
      case "AMAZON": return "#FF9900";
      case "FLIPKART": return "#2874F0";
      default: return "#4B5563";
    }
  };

  const getStoreInitial = (name) => {
    switch (name) {
      case "AMAZON": return "A";
      case "FLIPKART": return "F";
      default: return name ? name.charAt(0) : "S";
    }
  };

  const formatTimeAgo = (dateStr) => {
    if (!dateStr) return "Recently checked";
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
      return "Recently checked";
    }
  };

  return (
    <div className={`store-card ${isLowest && isLive ? "store-card-lowest" : ""} store-card-${storeUpper.toLowerCase()}`}>
      {isLowest && isLive && (
        <div className="lowest-ribbon">
          🏆 Lowest Price ({storeUpper})
        </div>
      )}

      <div className="store-header">
        <div className="store-identity">
          <div
            className="store-logo-badge"
            style={{ backgroundColor: getStoreLogoColor(storeUpper) }}
          >
            {getStoreInitial(storeUpper)}
          </div>
          <div className="store-title-wrap">
            <h4 className="store-title">{storeUpper}</h4>
            <div className="store-meta-tags">
              <span className={`status-tag status-${(status || "default").toLowerCase().replace("_", "-")}`}>
                {status === "LIVE" && "• LIVE"}
                {status === "CONFIG_REQUIRED" && "CONFIG REQUIRED"}
                {status === "UNAVAILABLE" && "UNAVAILABLE"}
                {status === "FETCH_FAILED" && "FETCH FAILED"}
                {!["LIVE", "CONFIG_REQUIRED", "UNAVAILABLE", "FETCH_FAILED"].includes(status) && status}
              </span>
              <span className="source-label">
                Direct Provider API
              </span>
            </div>
          </div>
        </div>

        <div className="store-availability">
          {isUnavailable ? (
            <span className="avail-badge avail-out"><FaTimes /> Unavailable</span>
          ) : (
            <span className="avail-badge avail-in"><FaCheck /> {availability?.replace("_", " ") || "IN STOCK"}</span>
          )}
        </div>
      </div>

      <div className="store-price-box">
        {isUnavailable ? (
          <div className="price-stack">
            <span className="price-unavailable">Price Unavailable</span>
          </div>
        ) : (
          <div className="price-stack">
            <span className="current-currency">₹</span>
            <span className="current-amount">{price.toLocaleString("en-IN")}</span>
          </div>
        )}

        <span className="last-checked">
          <FaClock /> {formatTimeAgo(lastCheckedAt)}
        </span>
      </div>

      <div className="store-actions">
        {productUrl && isLive ? (
          <a
            href={productUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={`buy-btn buy-btn-${storeUpper.toLowerCase()}`}
          >
            Buy on {storeUpper} <FaExternalLinkAlt className="ext-icon" />
          </a>
        ) : (
          <button disabled className="buy-btn disabled">
            {status === "CONFIG_REQUIRED"
              ? "API Config Required"
              : "No Result Available"}
          </button>
        )}

        <button
          type="button"
          className="track-btn"
          onClick={() => isLive && id && onTrack && onTrack(storePrice)}
          disabled={!isLive || !id}
          title={!isLive ? "Tracking unavailable for this listing" : `Track price on ${storeUpper}`}
        >
          <FaBell /> Track Price
        </button>
      </div>
    </div>
  );
}

export default Storecard;
