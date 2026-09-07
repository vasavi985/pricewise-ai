import React from "react";
import { Link } from "react-router-dom";
import { FaTag, FaCheckCircle, FaStar, FaStore } from "react-icons/fa";
import "../styles/productcard.css";

function Productcard({ product }) {
  if (!product) return null;

  const {
    productId,
    productName,
    brand,
    category,
    imageUrl,
    rating,
    lowestPrice,
    highestPrice,
    savingsAmount,
    savingsPercentage,
    bestStore,
    stores = [],
    recommendation,
  } = product;

  return (
    <div className="product-card">
      <div className="product-card-media">
        <img
          src={imageUrl || "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80"}
          alt={productName}
          className="product-card-img"
          onError={(e) => {
            e.target.src = "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
          }}
        />
        {bestStore && (
          <span className={`card-badge-best ${bestStore === "CATALOG" ? "badge-catalog" : ""}`}>
            {bestStore === "CATALOG" ? "📌 Catalog Benchmark" : `🏆 Lowest: ${bestStore}`}
          </span>
        )}
      </div>

      <div className="product-card-content">
        <div className="card-meta">
          {brand && <span className="card-brand">{brand}</span>}
          {category && <span className="card-category">• {category}</span>}
          {rating && (
            <span className="card-rating">
              <FaStar className="star-icon" /> {rating.toFixed(1)}
            </span>
          )}
        </div>

        <h3 className="card-title">
          <Link to={`/product/${productId}`}>{productName}</Link>
        </h3>

        {/* Pricing Box */}
        <div className="card-pricing">
          <div className="price-main">
            <span className="price-label">{bestStore === "CATALOG" ? "Reference Benchmark" : "Best Live Price"}</span>
            <span className="price-value">
              {lowestPrice ? `₹${lowestPrice.toLocaleString("en-IN")}` : "Check Stores"}
            </span>
          </div>

          {savingsAmount > 0 && bestStore !== "CATALOG" && (
            <div className="savings-pill">
              Save ₹{savingsAmount.toLocaleString("en-IN")} ({savingsPercentage}%)
            </div>
          )}
        </div>

        {/* Store mini list */}
        {stores.length > 0 && (
          <div className="card-stores">
            <span className="stores-count-label">
              <FaStore /> {stores.length} store{stores.length > 1 ? "s" : ""} compared:
            </span>
            <div className="store-chips">
              {stores.slice(0, 3).map((s) => (
                <div key={s.id || s.store} className={`store-chip ${s.lowest && s.status === "LIVE" ? "chip-lowest" : ""}`}>
                  <span className="chip-name">{s.store === "CATALOG" ? "Catalog" : s.store}</span>
                  <span className="chip-price">
                    {s.price ? `₹${s.price.toLocaleString("en-IN")}` : (s.status === "CONFIG_REQUIRED" ? "Unconfigured" : "Unavailable")}
                  </span>
                </div>
              ))}
              {stores.length > 3 && (
                <span className="more-stores-tag">+{stores.length - 3} more</span>
              )}
            </div>
          </div>
        )}

        <div className="card-actions">
          <Link to={`/product/${productId}`} className="view-details-btn">
            Compare All Prices &amp; Track →
          </Link>
        </div>
      </div>
    </div>
  );
}

export default Productcard;
