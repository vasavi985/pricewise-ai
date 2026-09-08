import React, { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Historycard from "../components/Historycard";
import trackingService from "../services/trackingService";
import { useAuth } from "../context/AuthContext";
import { FaBell, FaSearch, FaSpinner, FaCheckCircle } from "react-icons/fa";
import "../styles/tracking.css";

function Tracking() {
  const { currentUser, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [trackedItems, setTrackedItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!authLoading && !currentUser) {
      navigate("/login", {
        replace: true,
        state: { from: location, message: "Please log in to view and manage your tracked products." },
      });
    }
  }, [currentUser, authLoading, navigate, location]);

  useEffect(() => {
    if (currentUser) {
      loadTrackedItems();
    }
  }, [currentUser]);

  const loadTrackedItems = async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await trackingService.getTrackedProducts();
      setTrackedItems(Array.isArray(items) ? items : []);
    } catch (err) {
      setError(err.message || "Failed to load tracked items.");
    } finally {
      setLoading(false);
    }
  };

  const handleStopTracking = async (id) => {
    if (!window.confirm("Are you sure you want to stop tracking this product?")) return;
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

  if (authLoading || (!currentUser && !loading)) {
    return (
      <div className="tracking-page">
        <Navbar />
        <main className="tracking-container">
          <div className="tracking-loading">
            <FaSpinner className="spin-ico" />
            <p>Checking authentication...</p>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div className="tracking-page">
      <Navbar />

      <main className="tracking-container">
        <div className="tracking-header">
          <span className="tracking-badge">
            <FaBell /> Background Intelligence Active
          </span>
          <h1 className="tracking-title">Price Tracking Dashboard</h1>
          <p className="tracking-subtitle">
            Products you are actively monitoring. Our scheduler automatically inspects connected retailer prices every 60 seconds to detect price drops.
          </p>
        </div>

        {loading ? (
          <div className="tracking-loading">
            <FaSpinner className="spin-ico" />
            <p>Loading your tracked products...</p>
          </div>
        ) : error ? (
          <div className="tracking-error">
            <p>{error}</p>
            <button onClick={loadTrackedItems} className="retry-btn">Retry</button>
          </div>
        ) : trackedItems.length === 0 ? (
          <div className="tracking-empty">
            <span className="empty-bell">🔔</span>
            <h3>No Products Being Tracked Yet</h3>
            <p>
              When you find a product on PriceWise AI, click <strong>"Track Price"</strong> on any store listing to start automated background price checking and drop alerts.
            </p>
            <Link to="/results" className="start-tracking-btn">
              <FaSearch /> Discover Products to Track
            </Link>
          </div>
        ) : (
          <div className="tracking-list-wrapper">
            <div className="tracking-meta-bar">
              <span>
                Actively tracking <strong>{trackedItems.length}</strong> product{trackedItems.length > 1 ? "s" : ""}
              </span>
              <button onClick={loadTrackedItems} className="refresh-all-btn">
                ↻ Refresh All
              </button>
            </div>

            <div className="tracking-grid">
              {trackedItems.map((item) => (
                <Historycard
                  key={item.id}
                  item={item}
                  onStopTracking={handleStopTracking}
                  onCheckNow={handleCheckNow}
                />
              ))}
            </div>
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
}

export default Tracking;
