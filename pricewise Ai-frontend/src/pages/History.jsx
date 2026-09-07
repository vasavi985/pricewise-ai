import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Historycard from "../components/Historycard";
import trackingService from "../services/trackingService";
import productService from "../services/productService";
import { FaHistory, FaBell, FaSearch, FaStore, FaClock, FaSpinner } from "react-icons/fa";
import "../styles/history.css";

function History() {
  const [activeTab, setActiveTab] = useState("tracking"); // "tracking" or "searches"
  const [trackedItems, setTrackedItems] = useState([]);
  const [recentProducts, setRecentProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [trackingData, searchData] = await Promise.all([
        trackingService.getTrackedProducts().catch(() => []),
        productService.searchProducts("").catch(() => ({ results: [] })),
      ]);
      setTrackedItems(Array.isArray(trackingData) ? trackingData : []);
      setRecentProducts(searchData?.results || []);
    } finally {
      setLoading(false);
    }
  };

  const handleStopTracking = async (id) => {
    if (!window.confirm("Stop tracking this product?")) return;
    try {
      await trackingService.stopTracking(id);
      setTrackedItems((prev) => prev.filter((item) => item.id !== id));
    } catch (err) {
      alert("Failed to stop tracking: " + err.message);
    }
  };

  const handleCheckNow = async (id) => {
    try {
      const updated = await trackingService.checkPriceNow(id);
      setTrackedItems((prev) =>
        prev.map((item) => (item.id === id ? updated : item))
      );
    } catch (err) {
      alert("Failed to check live price: " + err.message);
    }
  };

  return (
    <div className="history-page">
      <Navbar />

      <main className="history-container">
        <div className="history-header">
          <h1 className="history-page-title">Activity &amp; Price Dashboard</h1>
          <p className="history-page-desc">
            Review your active price drop trackers, monitor periodic price verification checks, and explore recent catalog comparisons.
          </p>

          <div className="history-tabs">
            <button
              className={`tab-btn ${activeTab === "tracking" ? "tab-active" : ""}`}
              onClick={() => setActiveTab("tracking")}
            >
              <FaBell /> Tracked Products ({trackedItems.length})
            </button>
            <button
              className={`tab-btn ${activeTab === "searches" ? "tab-active" : ""}`}
              onClick={() => setActiveTab("searches")}
            >
              <FaHistory /> Verified Products Catalog ({recentProducts.length})
            </button>
          </div>
        </div>

        {loading ? (
          <div className="history-loading">
            <FaSpinner className="spin-ico" />
            <p>Loading activity and price records...</p>
          </div>
        ) : activeTab === "tracking" ? (
          <div className="tab-content">
            {trackedItems.length === 0 ? (
              <div className="history-empty-card">
                <FaBell className="he-icon" />
                <h3>No Active Price Trackers</h3>
                <p>
                  Click "Track Price" on any product comparison to enable automated scheduled price checks and drop alerts.
                </p>
                <Link to="/results" className="he-cta-btn">
                  <FaSearch /> Discover Products to Track
                </Link>
              </div>
            ) : (
              <div className="history-grid-view">
                {trackedItems.map((item) => (
                  <Historycard
                    key={item.id}
                    item={item}
                    onStopTracking={handleStopTracking}
                    onCheckNow={handleCheckNow}
                  />
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="tab-content">
            {recentProducts.length === 0 ? (
              <div className="history-empty-card">
                <FaHistory className="he-icon" />
                <h3>No Catalog Products Found</h3>
                <p>
                  Explore and compare deals on PriceWise AI to view verified product entries here.
                </p>
                <Link to="/results" className="he-cta-btn">
                  <FaSearch /> Discover Products
                </Link>
              </div>
            ) : (
              <div className="recent-checks-grid">
                {recentProducts.map((p) => (
                  <div key={p.productId} className="recent-check-card">
                    <img
                      src={p.imageUrl || "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80"}
                      alt={p.productName}
                      className="rc-thumb"
                    />
                    <div className="rc-info">
                      <span className="rc-brand">{p.brand || "Electronics"}</span>
                      <h4 className="rc-name">
                        <Link to={`/product/${p.productId}`}>{p.productName}</Link>
                      </h4>
                      <div className="rc-prices">
                        <span className="rc-low">
                          Lowest: ₹{p.lowestPrice ? p.lowestPrice.toLocaleString("en-IN") : "N/A"}
                        </span>
                        {p.bestStore && <span className="rc-store">({p.bestStore})</span>}
                      </div>
                    </div>
                    <Link to={`/product/${p.productId}`} className="rc-view-link">
                      View History &amp; Deals →
                    </Link>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
}

export default History;