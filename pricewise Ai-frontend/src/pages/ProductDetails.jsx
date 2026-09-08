import React, { useState, useEffect } from "react";
import { useParams, Link, useNavigate, useLocation } from "react-router-dom";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Storecard from "../components/Storecard";
import Recommendationcard from "../components/Recommendationcard";
import productService from "../services/productService";
import trackingService from "../services/trackingService";
import { useAuth } from "../context/AuthContext";
import {
  FaArrowLeft,
  FaBell,
  FaChartLine,
  FaStar,
  FaStore,
  FaSpinner,
  FaCheckCircle,
  FaTimes,
  FaHistory,
  FaInfoCircle,
} from "react-icons/fa";
import "../styles/productdetails.css";

function ProductDetails() {
  const { id } = useParams();
  const { currentUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [product, setProduct] = useState(null);
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Tracking modal state
  const [selectedStoreForTrack, setSelectedStoreForTrack] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [userEmail, setUserEmail] = useState("");
  const [targetPrice, setTargetPrice] = useState("");
  const [trackLoading, setTrackLoading] = useState(false);
  const [trackSuccess, setTrackSuccess] = useState(false);

  useEffect(() => {
    loadData();
  }, [id]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape" && isModalOpen) {
        setIsModalOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isModalOpen]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [prodData, histData] = await Promise.all([
        productService.getProductDetails(id),
        productService.getProductHistory(id).catch(() => null),
      ]);
      setProduct(prodData);
      setHistory(histData);
    } catch (err) {
      setError(err.message || "Failed to load product details.");
    } finally {
      setLoading(false);
    }
  };

  const handleOpenTrackModal = (storePrice) => {
    if (!currentUser) {
      navigate("/login", {
        state: { from: location, message: "Please log in to track prices and receive drop alerts." },
      });
      return;
    }
    if (!storePrice || !storePrice.id) {
      alert("This store listing is not configured or unavailable for tracking.");
      return;
    }
    setSelectedStoreForTrack(storePrice);
    if (storePrice?.price) {
      // Suggest 5% drop
      const suggested = Math.round(storePrice.price * 0.95);
      setTargetPrice(suggested.toString());
    }
    if (currentUser.email && !userEmail) {
      setUserEmail(currentUser.email);
    }
    setTrackSuccess(false);
    setIsModalOpen(true);
  };

  const handleSubmitTracking = async (e) => {
    e.preventDefault();
    if (!selectedStoreForTrack || !selectedStoreForTrack.id) return;

    const parsedPrice = parseFloat(targetPrice);
    if (isNaN(parsedPrice) || parsedPrice <= 0) {
      alert("Please enter a valid target price greater than ₹0.");
      return;
    }

    setTrackLoading(true);
    try {
      await trackingService.trackProduct({
        storeProductId: selectedStoreForTrack.id,
        userEmail: userEmail.trim(),
        targetPrice: parsedPrice,
        targetDropPercentage: 5.0,
      });
      setTrackSuccess(true);
      setTimeout(() => {
        setIsModalOpen(false);
        setTrackSuccess(false);
      }, 1800);
    } catch (err) {
      alert("Failed to start price tracking: " + err.message);
    } finally {
      setTrackLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="details-page">
        <Navbar />
        <div className="details-loading">
          <FaSpinner className="spin-ico" />
          <p>Loading real-time store comparisons and price history...</p>
        </div>
        <Footer />
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="details-page">
        <Navbar />
        <div className="details-error">
          <h2>Product Not Found</h2>
          <p>{error || "We could not locate this product."}</p>
          <Link to="/results" className="back-btn">
            <FaArrowLeft /> Back to Search
          </Link>
        </div>
        <Footer />
      </div>
    );
  }

  // Format Recharts data strictly from verified PriceRecord entries
  const chartData = history?.points?.map((pt) => ({
    date: pt.date,
    price: pt.price,
    store: pt.store,
    status: pt.status,
    source: pt.source,
  })) || [];

  return (
    <div className="details-page">
      <Navbar />

      <main className="details-container">
        {/* Navigation Breadcrumb */}
        <div className="breadcrumb-bar">
          <Link to="/results" className="breadcrumb-link">
            <FaArrowLeft /> Back to Results
          </Link>
          <span className="breadcrumb-curr">{product.productName}</span>
        </div>

        {/* Product Overview Section */}
        <section className="product-hero">
          <div className="product-media-column">
            <img
              src={product.imageUrl || "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80"}
              alt={product.productName}
              className="product-large-img"
              onError={(e) => {
                e.target.src = "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80";
              }}
            />
          </div>

          <div className="product-info-column">
            <div className="info-meta">
              {product.brand && <span className="brand-badge">{product.brand}</span>}
              {product.category && <span className="category-badge">{product.category}</span>}
              {product.rating && (
                <span className="rating-badge">
                  <FaStar /> {product.rating.toFixed(1)} / 5
                </span>
              )}
            </div>

            <h1 className="product-headline">{product.productName}</h1>

            <p className="product-description">
              {product.description || "Live multi-store product price intelligence. Comparing current live offers across verified stores."}
            </p>

            <div className="quick-summary-box">
              <div className="qs-item">
                <span className="qs-label">Lowest Verified Price</span>
                <span className="qs-val-green">
                  {product.lowestPrice ? `₹${product.lowestPrice.toLocaleString("en-IN")}` : "N/A"}
                </span>
                {product.bestStore && <span className="qs-sub">at {product.bestStore}</span>}
              </div>

              {product.savingsAmount > 0 && (
                <div className="qs-item">
                  <span className="qs-label">Maximum Savings</span>
                  <span className="qs-val-blue">
                    ₹{product.savingsAmount.toLocaleString("en-IN")}
                  </span>
                  <span className="qs-sub">{product.savingsPercentage}% lower</span>
                </div>
              )}

              <div className="qs-item">
                <span className="qs-label">Stores Compared</span>
                <span className="qs-val-dark">
                  {product.stores?.length || 0} Stores
                </span>
                <span className="qs-sub">Real live records</span>
              </div>
            </div>

            <div className="hero-cta-bar">
              {(() => {
                const trackableStore = product.stores?.find(
                  (s) => s.id && s.price != null && s.price > 0 && s.status !== "CONFIG_REQUIRED" && s.status !== "UNAVAILABLE"
                );
                return (
                  <button
                    type="button"
                    className="hero-track-btn"
                    onClick={() => trackableStore && handleOpenTrackModal(trackableStore)}
                    disabled={!trackableStore}
                    title={trackableStore ? `Track price at ${trackableStore.store}` : "No active store listings available to track"}
                  >
                    <FaBell /> Track Lowest Price &amp; Get Alerts
                  </button>
                );
              })()}
            </div>
          </div>
        </section>

        {/* AI Recommendation Banner */}
        <Recommendationcard product={product} />

        {/* Store Comparison Grid */}
        <section className="stores-comparison-section">
          <div className="section-head">
            <FaStore className="sec-icon" />
            <div>
              <h2 className="section-title">Store Price Comparison</h2>
              <p className="section-subtitle">
                Prices and availability checked across supported retailer channels.
              </p>
            </div>
          </div>

          {product.bestStore === "CATALOG" && (
            <div className="catalog-disclaimer-banner">
              <FaInfoCircle className="disclaimer-icon" />
              <div className="disclaimer-text">
                <strong>Internal Database Benchmark:</strong> The price shown below originates from the local reference catalog (SAMPLE DATA). Real-time price tracking is actively monitored across connected store providers.
              </div>
            </div>
          )}

          <div className="stores-grid-details">
            {product.stores?.map((sp) => (
              <Storecard
                key={sp.id || sp.store}
                storePrice={sp}
                onTrack={handleOpenTrackModal}
              />
            ))}
          </div>
        </section>

        {/* Historical Price Chart */}
        <section className="history-chart-section">
          <div className="section-head">
            <FaChartLine className="sec-icon chart-icon" />
            <div>
              <h2 className="section-title">Verified Price History</h2>
              <p className="section-subtitle">
                Immutable price records logged over time. Hover over points to see exact store prices.
              </p>
            </div>
          </div>

          <div className="chart-wrapper">
            {chartData.length > 0 ? (
              <ResponsiveContainer width="100%" height={320}>
                <LineChart data={chartData} margin={{ top: 15, right: 30, left: 20, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" />
                  <XAxis dataKey="date" stroke="#9CA3AF" tick={{ fontSize: 12 }} />
                  <YAxis
                    stroke="#9CA3AF"
                    tick={{ fontSize: 12 }}
                    domain={[(dataMin) => Math.max(0, Math.floor(dataMin * 0.9)), (dataMax) => Math.ceil(dataMax * 1.1)]}
                    tickFormatter={(val) => `₹${val.toLocaleString("en-IN")}`}
                  />
                  <Tooltip
                    formatter={(value, name, item) => [
                      `₹${Number(value).toLocaleString("en-IN")}`,
                      `${item.payload.store || "Price"} [${item.payload.status || "VERIFIED"}]`,
                    ]}
                    labelFormatter={(label) => `Logged Date: ${label}`}
                    contentStyle={{
                      backgroundColor: "#FFFFFF",
                      borderRadius: "12px",
                      border: "1px solid #E5E7EB",
                      boxShadow: "0 4px 15px rgba(0,0,0,0.08)",
                    }}
                  />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="price"
                    name="Recorded Price"
                    stroke="#6C4CF1"
                    strokeWidth={3}
                    dot={{ fill: "#6C4CF1", strokeWidth: 2, r: 4 }}
                    activeDot={{ r: 7 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="empty-chart-notice">
                <FaHistory />
                <p>Tracking has just been initiated for this product. Price records will display as new data points are logged.</p>
              </div>
            )}

            {history && (
              <div className="chart-stat-strip">
                <div className="css-stat">
                  <span className="css-lbl">Current Price</span>
                  <span className="css-num blue">
                    {product.lowestPrice ? `₹${product.lowestPrice.toLocaleString("en-IN")}` : "N/A"}
                  </span>
                </div>
                <div className="css-stat">
                  <span className="css-lbl">Lowest Recorded</span>
                  <span className="css-num green">
                    {history.lowestRecorded ? `₹${history.lowestRecorded.toLocaleString("en-IN")}` : "N/A"}
                  </span>
                </div>
                <div className="css-stat">
                  <span className="css-lbl">Highest Recorded</span>
                  <span className="css-num red">
                    {history.highestRecorded ? `₹${history.highestRecorded.toLocaleString("en-IN")}` : "N/A"}
                  </span>
                </div>
                <div className="css-stat">
                  <span className="css-lbl">Average Price</span>
                  <span className="css-num purple">
                    {history.averageRecorded ? `₹${history.averageRecorded.toLocaleString("en-IN")}` : "N/A"}
                  </span>
                </div>
                <div className="css-stat">
                  <span className="css-lbl">Last Checked</span>
                  <span className="css-num">
                    {history.lastChecked ? new Date(history.lastChecked).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : "Recently"}
                  </span>
                </div>
              </div>
            )}
          </div>
        </section>
      </main>

      {/* Tracking Modal */}
      {isModalOpen && (
        <div className="modal-overlay" onClick={() => setIsModalOpen(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Track Price for {selectedStoreForTrack?.store}</h3>
              <button className="modal-close-btn" onClick={() => setIsModalOpen(false)}>
                <FaTimes />
              </button>
            </div>

            {trackSuccess ? (
              <div className="modal-success-state">
                <FaCheckCircle className="success-ico" />
                <h4>Price Tracking Active!</h4>
                <p>
                  You will be notified when this listing drops below ₹{Number(targetPrice).toLocaleString("en-IN")}.
                </p>
              </div>
            ) : (
              <form className="modal-form" onSubmit={handleSubmitTracking}>
                <p className="modal-desc">
                  Set your target price. Our backend scheduler checks live prices periodically and alerts you when the price drops.
                </p>

                <div className="form-group">
                  <label>Current Price</label>
                  <div className="form-val-display">
                    ₹{selectedStoreForTrack?.price?.toLocaleString("en-IN") || "N/A"}
                  </div>
                </div>

                <div className="form-group">
                  <label htmlFor="targetPrice">Notify Me When Price Drops To / Below (₹)</label>
                  <input
                    id="targetPrice"
                    type="number"
                    min="1"
                    step="1"
                    required
                    value={targetPrice}
                    onChange={(e) => setTargetPrice(e.target.value)}
                    placeholder="Enter target price in INR"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="userEmail">Your Email Address (Optional)</label>
                  <input
                    id="userEmail"
                    type="email"
                    value={userEmail}
                    onChange={(e) => setUserEmail(e.target.value)}
                    placeholder="you@example.com (for drop alerts)"
                  />
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    className="modal-cancel-btn"
                    onClick={() => setIsModalOpen(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="modal-submit-btn"
                    disabled={trackLoading}
                  >
                    {trackLoading ? "Setting alert..." : "Start Tracking Price"}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      <Footer />
    </div>
  );
}

export default ProductDetails;
