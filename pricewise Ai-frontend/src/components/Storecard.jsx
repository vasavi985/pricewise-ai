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

  const getStoreLogoColor = (name) => {
    switch (name?.toUpperCase()) {
      case "AMAZON": return "#FF9900";
      case "FLIPKART": return "#2874F0";
      case "CROMA": return "#00B67A";
      case "OPEN_COMMERCE": return "#6C4CF1";
      default: return "#4B5563";
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
    <div className={`store-card ${isLowest && status === "LIVE" ? "store-card-lowest" : ""} ${isUnavailable ? "store-card-disabled" : ""}`}>
      {isLowest && status === "LIVE" && (
        <div className="lowest-ribbon">
          🏆 Lowest Verified Price
        </div>
      )}
      {status === "SAMPLE_DATA" && (
        <div className="catalog-ribbon">
          📌 Catalog Benchmark
        </div>
      )}

      <div className="store-header">
        <div className="store-identity">
          <div
            className="store-logo-badge"
            style={{ backgroundColor: getStoreLogoColor(store) }}
          >
            {store === "CATALOG" ? "DB" : store?.substring(0, 1).toUpperCase()}
          </div>
          <div>
            <h4 className="store-title">{store === "CATALOG" ? "Reference Catalog" : store}</h4>
            <div className="store-meta-tags">
              <span className={`status-tag status-${(status || "default").toLowerCase().replace("_", "-")}`}>
                {status === "LIVE" && "● LIVE"}
                {status === "SAMPLE_DATA" && "SAMPLE DATA"}
                {status === "LAST_KNOWN" && "LAST KNOWN"}
                {status === "CONFIG_REQUIRED" && "CONFIG REQUIRED"}
                {status === "UNAVAILABLE" && "UNAVAILABLE"}
                {status === "FETCH_FAILED" && "FETCH FAILED"}
                {!["LIVE", "SAMPLE_DATA", "LAST_KNOWN", "CONFIG_REQUIRED", "UNAVAILABLE", "FETCH_FAILED"].includes(status) && status}
              </span>
              <span className="source-label">
                {status === "LIVE" ? "Live Commerce API" : status === "SAMPLE_DATA" ? "Internal Catalog Baseline" : "Direct Provider"}
              </span>
            </div>
          </div>
        </div>

        <div className="store-availability">
          {isUnavailable ? (
            <span className="avail-badge avail-out"><FaTimes /> Unavailable</span>
          ) : (
            <span className="avail-badge avail-in"><FaCheck /> {availability.replace("_", " ")}</span>
          )}
        </div>
      </div>

      <div className="store-price-box">
        {isUnavailable ? (
          <span className="price-unavailable">Price Unavailable</span>
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
        {productUrl && !isUnavailable && status === "LIVE" ? (
          <a
            href={productUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="buy-btn"
          >
            Buy on {store} <FaExternalLinkAlt className="ext-icon" />
          </a>
        ) : (
          <button disabled className="buy-btn disabled">
            {status === "CONFIG_REQUIRED"
              ? "API Config Required"
              : status === "UNAVAILABLE"
              ? "Integration Unavailable"
              : status === "SAMPLE_DATA"
              ? "Sample Benchmark (Not Purchasable)"
              : "Not Available"}
          </button>
        )}

        {!isUnavailable && status !== "CONFIG_REQUIRED" && status !== "UNAVAILABLE" && storePrice.id && onTrack && (
          <button
            type="button"
            className="track-btn"
            onClick={() => onTrack(storePrice)}
          >
            <FaBell /> Track Price
          </button>
        )}
      </div>
    </div>
  );
}

export default Storecard;
