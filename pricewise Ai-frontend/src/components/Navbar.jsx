import React, { useState, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { FaBell, FaSearch, FaHistory, FaInfoCircle, FaHome, FaChartLine } from "react-icons/fa";
import trackingService from "../services/trackingService";
import "../styles/navbar.css";

function Navbar() {
  const location = useLocation();
  const [trackedCount, setTrackedCount] = useState(0);

  useEffect(() => {
    trackingService.getTrackedProducts()
      .then((items) => {
        if (Array.isArray(items)) {
          setTrackedCount(items.length);
        }
      })
      .catch(() => {});
  }, [location.pathname]);

  return (
    <nav className="navbar">
      <Link to="/" className="logo">
        <span className="logo-sparkle">✨</span>
        <h2>PriceWise <span>AI</span></h2>
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
      </div>
    </nav>
  );
}

export default Navbar;