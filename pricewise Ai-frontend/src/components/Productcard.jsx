import React from "react";
import { Link } from "react-router-dom";
import { FaStar, FaExternalLinkAlt } from "react-icons/fa";
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
    savingsAmount,
    savingsPercentage,
    bestStore,
    stores = [],
  } = product;

  const amazonStore = stores.find((s) => s.store?.toUpperCase() === "AMAZON");
  const flipkartStore = stores.find((s) => s.store?.toUpperCase() === "FLIPKART");

  const hasAmazonPrice = amazonStore && amazonStore.price != null && amazonStore.price > 0 && amazonStore.status === "LIVE";
  const hasFlipkartPrice = flipkartStore && flipkartStore.price != null && flipkartStore.price > 0 && flipkartStore.status === "LIVE";
  const bothAvailable = hasAmazonPrice && hasFlipkartPrice;

  // Determine cheaper store when both exist
  let cheaperStore = null;
  if (bothAvailable) {
    if (amazonStore.price < flipkartStore.price) {
      cheaperStore = "AMAZON";
    } else if (flipkartStore.price < amazonStore.price) {
      cheaperStore = "FLIPKART";
    } else {
      cheaperStore = "EQUAL";
    }
  }

  const comparisonCountText = bothAvailable
    ? "2 stores compared:"
    : hasAmazonPrice
    ? "1 store available:"
    : hasFlipkartPrice
    ? "1 store available:"
    : "Price check:";

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
        {bothAvailable && cheaperStore && cheaperStore !== "EQUAL" && (
          <span className="card-badge-best">
            🏆 Lowest: {cheaperStore === "AMAZON" ? "Amazon" : "Flipkart"}
          </span>
        )}
        {bothAvailable && cheaperStore === "EQUAL" && (
          <span className="card-badge-best badge-equal">
            ⚖️ Same Price on Both
          </span>
        )}
        {!bothAvailable && hasAmazonPrice && (
          <span className="card-badge-best badge-single">
            Amazon Only
          </span>
        )}
        {!bothAvailable && hasFlipkartPrice && (
          <span className="card-badge-best badge-single">
            Flipkart Only
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

        {/* Amazon vs Flipkart Comparison Box */}
        <div className="comparison-box">
          <div className="comparison-header">
            <span className="comparison-count-label">{comparisonCountText}</span>
          </div>

          {/* Amazon Row */}
          <div className={`store-compare-row ${cheaperStore === "AMAZON" ? "cheaper-row" : ""}`}>
            <div className="store-identity-col">
              <span className="store-pill-badge amazon-pill">Amazon</span>
            </div>
            <div className="store-price-col">
              {hasAmazonPrice ? (
                <span className="store-price-val">₹{amazonStore.price.toLocaleString("en-IN")}</span>
              ) : (
                <span className="store-unavailable">No matching result</span>
              )}
            </div>
            <div className="store-action-col">
              {hasAmazonPrice && amazonStore.productUrl ? (
                <a
                  href={amazonStore.productUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="store-buy-link amazon-link"
                >
                  View on Amazon <FaExternalLinkAlt className="ext-icon" />
                </a>
              ) : (
                <span className="store-action-empty">—</span>
              )}
            </div>
          </div>

          {/* Flipkart Row */}
          <div className={`store-compare-row ${cheaperStore === "FLIPKART" ? "cheaper-row" : ""}`}>
            <div className="store-identity-col">
              <span className="store-pill-badge flipkart-pill">Flipkart</span>
            </div>
            <div className="store-price-col">
              {hasFlipkartPrice ? (
                <span className="store-price-val">₹{flipkartStore.price.toLocaleString("en-IN")}</span>
              ) : (
                <span className="store-unavailable">No matching result</span>
              )}
            </div>
            <div className="store-action-col">
              {hasFlipkartPrice && flipkartStore.productUrl ? (
                <a
                  href={flipkartStore.productUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="store-buy-link flipkart-link"
                >
                  View on Flipkart <FaExternalLinkAlt className="ext-icon" />
                </a>
              ) : (
                <span className="store-action-empty">—</span>
              )}
            </div>
          </div>
        </div>

        {/* Savings Callout - ONLY when both real prices exist and savings > 0 */}
        {bothAvailable && savingsAmount > 0 && (
          <div className="savings-banner">
            <span className="savings-highlight">
              ✓ You save ₹{savingsAmount.toLocaleString("en-IN")} ({savingsPercentage}%) by choosing {cheaperStore === "AMAZON" ? "Amazon" : "Flipkart"}
            </span>
          </div>
        )}

        <div className="card-actions">
          <Link to={`/product/${productId}`} className="view-details-btn">
            View Details &amp; Price History →
          </Link>
        </div>
      </div>
    </div>
  );
}

export default Productcard;
