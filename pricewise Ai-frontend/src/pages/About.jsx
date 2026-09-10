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
      .then((data) => {
        const raw = Array.isArray(data) ? data : [];
        setProviders(raw.filter((p) => ["AMAZON", "FLIPKART"].includes(p.store?.toUpperCase())));
      })
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
            PriceWise AI is a focused price comparison platform engineered to compare live prices between Amazon and Flipkart. We help consumers find genuine savings, discover historical trends, and receive automated notifications when real price drops happen.
          </p>
        </div>

        <section className="about-principles">
          <div className="principle-card">
            <div className="p-icon"><FaShieldAlt /></div>
            <h3>100% Real Commerce Data</h3>
            <p>
              We only show verified <strong>LIVE</strong> prices directly from Amazon and Flipkart APIs. Zero fake catalogs, zero synthetic products, and zero artificial discounts.
            </p>
          </div>

          <div className="principle-card">
            <div className="p-icon"><FaServer /></div>
            <h3>Concurrent Live Querying</h3>
            <p>
              Every search executes concurrent queries against Amazon and Flipkart. Independent timeouts and fallback handling ensure resilient responses without cross-store interference.
            </p>
          </div>

          <div className="principle-card">
            <div className="p-icon"><FaChartLine /></div>
            <h3>Immutable Price History</h3>
            <p>
              Price records are never overwritten. Every price retrieval creates an append-only time-series record, producing genuine historical price charts for both Amazon and Flipkart.
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
            <h2>Active Store Integrations &amp; Status</h2>
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
                      <strong>{p.store === "AMAZON" ? "Amazon" : p.store === "FLIPKART" ? "Flipkart" : p.store}</strong>
                    </td>
                    <td>{p.description}</td>
                    <td>
                      <span className={`table-status-pill pill-${p.status.toLowerCase()}`}>
                        {p.status === "LIVE" ? "● Live Verified" : p.status === "CONFIG_REQUIRED" ? "API Key Required" : "Unavailable"}
                      </span>
                    </td>
                    <td className="config-cell">
                      <code>{p.requiredConfig || "Configured"}</code>
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