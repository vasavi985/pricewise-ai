import React, { useState, useEffect } from "react";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import productService from "../services/productService";
import { FaShieldAlt, FaServer, FaChartLine, FaBell, FaStore } from "react-icons/fa";
import "../styles/about.css";

function About() {
  const [providers, setProviders] = useState([]);

  useEffect(() => {
    productService.getProviders()
      .then((data) => setProviders(Array.isArray(data) ? data : []))
      .catch(() => {});
  }, []);

  return (
    <div className="about-page">
      <Navbar />

      <main className="about-container">
        <div className="about-hero">
          <span className="about-badge">Transparent Price Intelligence</span>
          <h1 className="about-title">About PriceWise AI</h1>
          <p className="about-lead">
            PriceWise AI is an open, production-style price tracking and intelligence platform engineered to help consumers discover live multi-store pricing, track historical price movements, and receive automated notifications when real price drops happen.
          </p>
        </div>

        <section className="about-principles">
          <div className="principle-card">
            <div className="p-icon"><FaShieldAlt /></div>
            <h3>Zero Fake Data</h3>
            <p>
              We distinguish clearly between <strong>LIVE</strong> prices, <strong>CATALOG</strong> verified data, and <strong>UNCONFIGURED</strong> integrations. We never invent fictitious prices or pretend that outdated data is live.
            </p>
          </div>

          <div className="principle-card">
            <div className="p-icon"><FaServer /></div>
            <h3>Provider Abstraction</h3>
            <p>
              Stores are isolated behind a clean <code>PriceProvider</code> interface. Official APIs (Amazon PA-API 5.0, Flipkart Affiliate API, Live Commerce) are called respecting rate limits and terms of service.
            </p>
          </div>

          <div className="principle-card">
            <div className="p-icon"><FaChartLine /></div>
            <h3>Immutable Price History</h3>
            <p>
              Price records are never overwritten. Every price retrieval creates an append-only time-series record, producing genuine historical price charts.
            </p>
          </div>

          <div className="principle-card">
            <div className="p-icon"><FaBell /></div>
            <h3>Automated Scheduler</h3>
            <p>
              Our background scheduler continuously checks tracked products, calculates real price drop percentages, and logs or dispatches notifications the moment a target is met.
            </p>
          </div>
        </section>

        {/* Live Provider Status Inspection Table */}
        <section className="about-providers-section">
          <div className="sec-header">
            <FaStore className="sec-ico" />
            <h2>Supported Store Integrations &amp; Status</h2>
          </div>

          <div className="provider-table-wrapper">
            <table className="provider-table">
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Integration Type</th>
                  <th>Current Status</th>
                  <th>Required Configuration</th>
                </tr>
              </thead>
              <tbody>
                {providers.map((p) => (
                  <tr key={p.store}>
                    <td className="store-cell">
                      <strong>{p.store}</strong>
                    </td>
                    <td>{p.description}</td>
                    <td>
                      <span className={`table-status-pill pill-${p.status.toLowerCase()}`}>
                        {p.status === "LIVE" ? "● Live Verified" : p.status === "SAMPLE_DATA" ? "Catalog Verified" : p.status === "CONFIG_REQUIRED" ? "API Key Required" : "Unavailable"}
                      </span>
                    </td>
                    <td className="config-cell">
                      <code>{p.requiredConfig || "Ready out of the box"}</code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}

export default About;