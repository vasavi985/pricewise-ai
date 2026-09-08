import React, { useState, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { FaBell, FaSearch, FaHistory, FaInfoCircle, FaHome, FaChartLine, FaUser, FaSignOutAlt, FaSignInAlt } from "react-icons/fa";
import { useAuth } from "../context/AuthContext";
import trackingService from "../services/trackingService";
import logoImg from "../assets/logos/pricewise-ai-logo-option-6.png";
import "../styles/navbar.css";

function Navbar() {
  const location = useLocation();
  const [trackedCount, setTrackedCount] = useState(0);
  const { currentUser, logout } = useAuth();

  useEffect(() => {
    if (!currentUser) {
      setTrackedCount(0);
      return;
    }
    trackingService.getTrackedProducts()
      .then((items) => {
        if (Array.isArray(items)) {
          setTrackedCount(items.length);
        }
      })
      .catch(() => {});
  }, [location.pathname, currentUser]);

  return (
    <nav className="navbar">
      <Link to="/" className="logo" aria-label="PriceWise AI Home">
        <img
          src={logoImg}
          alt="PriceWise AI"
          className="navbar-logo-img"
        />
      </Link>

      <ul className="nav-links">
        <li>
          <Link to="/" className={location.pathname === "/" ? "active" : ""}>
            <FaHome className="nav-icon" /> Home
          </Link>
        </li>
        <li>
          <Link to="/results" className={location.pathname === "/results" ? "active" : ""}>
            <FaSearch className="nav-icon" /> Compare Deals
          </Link>
        </li>
        <li>
          <Link to="/tracking" className={location.pathname === "/tracking" ? "active" : ""}>
            <FaBell className="nav-icon" /> Tracking
            {trackedCount > 0 && <span className="nav-count-badge">{trackedCount}</span>}
          </Link>
        </li>
        <li>
          <Link to="/history" className={location.pathname === "/history" ? "active" : ""}>
            <FaHistory className="nav-icon" /> History
          </Link>
        </li>
        <li>
          <Link to="/about" className={location.pathname === "/about" ? "active" : ""}>
            <FaInfoCircle className="nav-icon" /> About
          </Link>
        </li>
      </ul>

      <div className="nav-buttons">
        <Link to="/results" className="start-btn">
          <span>Search Prices</span>
        </Link>
        {currentUser ? (
          <div className="nav-user-area">
            <span className="nav-user-greeting">
              <FaUser className="nav-user-ico" /> Hi, {currentUser.displayName || currentUser.email?.split("@")[0]}
            </span>
            <button onClick={logout} className="nav-logout-btn" title="Log Out">
              <FaSignOutAlt /> Logout
            </button>
          </div>
        ) : (
          <Link to="/login" className="nav-login-btn">
            <FaSignInAlt /> Login
          </Link>
        )}
      </div>
    </nav>
  );
}

export default Navbar;