import React, { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Historycard from "../components/Historycard";
import trackingService from "../services/trackingService";
import historyService from "../services/historyService";
import { useAuth } from "../context/AuthContext";
import { FaHistory, FaBell, FaSearch, FaTrashAlt, FaClock, FaSpinner, FaArrowRight } from "react-icons/fa";
import "../styles/history.css";

function History() {
  const { currentUser, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [activeTab, setActiveTab] = useState("searches"); // default to user searches
  const [trackedItems, setTrackedItems] = useState([]);
  const [searchHistory, setSearchHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  // Protect history page: redirect to login if unauthenticated
  useEffect(() => {
    if (!authLoading && !currentUser) {
      navigate("/login", {
        replace: true,
        state: { from: location, message: "Please log in to view your search history." },
      });
    }
  }, [currentUser, authLoading, navigate, location]);

  useEffect(() => {
    if (currentUser) {
      loadData();
    }
  }, [currentUser]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [trackingData, historyData] = await Promise.all([
        trackingService.getTrackedProducts().catch(() => []),
        historyService.getUserHistory().catch(() => []),
      ]);
      setTrackedItems(Array.isArray(trackingData) ? trackingData : []);
      setSearchHistory(Array.isArray(historyData) ? historyData : []);
    } finally {
      setLoading(false);
    }
  };

  const handleClearHistory = async () => {
    if (!window.confirm("Are you sure you want to clear your search history? This cannot be undone.")) return;
    try {
      await historyService.clearUserHistory();
      setSearchHistory([]);
    } catch (err) {
      alert("Failed to clear search history: " + (err.message || "Unknown error"));
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

  const formatTimestamp = (ts) => {
    if (!ts) return "Recently";
    try {
      const date = new Date(ts);
      return date.toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return String(ts);
    }
  };

  if (authLoading || (!currentUser && !loading)) {
    return (
      <div className="history-page">
        <Navbar />
        <main className="history-container">
          <div className="history-loading">
            <FaSpinner className="spin-ico" />
            <p>Checking authentication...</p>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div className="history-page">
      <Navbar />

      <main className="history-container">
        <div className="history-header">
          <h1 className="history-page-title">Your Activity &amp; Price History</h1>
          <p className="history-page-desc">
            Review your personal search history, manage active price drop alerts, and monitor live store comparisons.
          </p>

          <div className="history-tabs">
            <button
              className={`tab-btn ${activeTab === "searches" ? "tab-active" : ""}`}
              onClick={() => setActiveTab("searches")}
            >
              <FaHistory /> Your Search History ({searchHistory.length})
            </button>
            <button
              className={`tab-btn ${activeTab === "tracking" ? "tab-active" : ""}`}
              onClick={() => setActiveTab("tracking")}
            >
              <FaBell /> Tracked Products ({trackedItems.length})
            </button>
          </div>
        </div>

        {loading ? (
          <div className="history-loading">
            <FaSpinner className="spin-ico" />
            <p>Loading your personal history and price trackers...</p>
          </div>
        ) : activeTab === "searches" ? (
          <div className="tab-content">
            <div className="history-actions-bar">
              <span className="history-section-title">
                Personal Search Records for {currentUser?.displayName || currentUser?.email}
              </span>
              {searchHistory.length > 0 && (
                <button onClick={handleClearHistory} className="clear-history-btn">
                  <FaTrashAlt /> Clear History
                </button>
              )}
            </div>

            {searchHistory.length === 0 ? (
              <div className="history-empty-card">
                <FaHistory className="he-icon" />
                <h3>No Search History Yet</h3>
                <p>
                  Search for products like smartphones, laptops, or headphones across stores to build your personal comparison history.
                </p>
                <Link to="/results" className="he-cta-btn">
                  <FaSearch /> Start Searching
                </Link>
              </div>
            ) : (
              <div className="search-history-grid">
                {searchHistory.map((item, idx) => (
                  <div key={item.id || idx} className="search-history-card">
                    <div className="sh-left">
                      <div className="sh-icon-wrap">
                        <FaSearch />
                      </div>
                      <div className="sh-details">
                        <span className="sh-query">{item.query}</span>
                        <div className="sh-meta">
                          <span><FaClock /> {formatTimestamp(item.createdAt)}</span>
                          {item.resultCount !== undefined && (
                            <span>• {item.resultCount} deals found</span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="sh-right">
                      {item.topProductName && (
                        <div className="sh-top-match">
                          {item.topProductImage && (
                            <img
                              src={item.topProductImage}
                              alt={item.topProductName}
                              className="sh-top-thumb"
                            />
                          )}
                          <div className="sh-top-text">
                            <span className="sh-top-name">{item.topProductName}</span>
                            {item.topProductPrice && (
                              <span className="sh-top-price">
                                From ₹{item.topProductPrice.toLocaleString("en-IN")}
                                {item.topProductStore && ` on ${item.topProductStore}`}
                              </span>
                            )}
                          </div>
                        </div>
                      )}
                      <Link
                        to={`/results?query=${encodeURIComponent(item.query)}`}
                        className="sh-repeat-btn"
                      >
                        Search Again <FaArrowRight />
                      </Link>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
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
        )}
      </main>

      <Footer />
    </div>
  );
}

export default History;