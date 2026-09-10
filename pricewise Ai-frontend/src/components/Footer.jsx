import React from "react";
import { Link } from "react-router-dom";
import "../styles/footer.css";

function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-container">
        <div className="footer-brand">
          <h3>PriceWise <span>AI</span></h3>
          <p>
            An open, transparent real Amazon vs Flipkart price intelligence and comparison platform.
            Track real product prices, discover historical trends, and never overpay.
          </p>
        </div>

        <div className="footer-links-col">
          <h4>Navigation</h4>
          <ul>
            <li><Link to="/">Home</Link></li>
            <li><Link to="/results">Compare Deals</Link></li>
            <li><Link to="/tracking">Price Tracking</Link></li>
            <li><Link to="/history">Price History</Link></li>
            <li><Link to="/about">About &amp; Architecture</Link></li>
          </ul>
        </div>

        <div className="footer-links-col">
          <h4>Store Integrations</h4>
          <ul>
            <li><span>Amazon (Real-Time API)</span></li>
            <li><span>Flipkart (Real-Time API)</span></li>
          </ul>
        </div>
      </div>

      <div className="footer-bottom">
        <p>© {new Date().getFullYear()} PriceWise AI. Amazon and Flipkart product names, logos, and brands are property of their respective owners.</p>
      </div>
    </footer>
  );
}

export default Footer;
