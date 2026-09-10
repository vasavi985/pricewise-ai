import React, { useState, useEffect } from "react";
import { useSearchParams, Link } from "react-router-dom";
import Navbar from "../components/Navbar";
import Footer from "../components/Footer";
import Searchbar from "../components/Searchbar";
import Productcard from "../components/Productcard";
import productService from "../services/productService";
import { FaStore, FaExclamationCircle, FaSpinner } from "react-icons/fa";
import "../styles/results.css";

function Results() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get("q") || searchParams.get("query") || "";

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchData, setSearchData] = useState({
    results: [],
    providers: [],
    totalFound: 0,
  });

  useEffect(() => {
    fetchResults(query);
  }, [query]);

  const fetchResults = async (searchQuery) => {
    setLoading(true);
    setError(null);
    try {
      const data = await productService.searchProducts(searchQuery);
      setSearchData({
        results: data?.results || [],
        providers: data?.providers || [],
        totalFound: data?.totalFound || 0,
      });
    } catch (err) {
      setError(err.message || "Failed to load product deals. Ensure backend is running.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="results-page">
      <Navbar />

      <main className="results-container">
        {/* Search header with bar */}
        <div className="results-header-section">
          <h1 className="results-title">
            {query ? `Search Results for "${query}"` : "All Tracked Products & Deals"}
          </h1>
          <Searchbar initialQuery={query} />
        </div>

        {/* Provider integration status banner */}
        {searchData.providers?.length > 0 && (
          <div className="provider-status-bar">
            <span className="ps-label"><FaStore /> Live Providers:</span>
            <div className="ps-list">
              {searchData.providers
                .filter((p) => ["AMAZON", "FLIPKART"].includes(p.store?.toUpperCase()))
                .map((p) => (
                  <div
                    key={p.store}
                    className={`ps-item ps-${p.status.toLowerCase()}`}
                    title={`${p.description} — ${p.requiredConfig || "Active"}`}
                  >
                    <span className="ps-dot"></span>
                    <span className="ps-name">{p.store === "AMAZON" ? "Amazon (Real-Time)" : p.store === "FLIPKART" ? "Flipkart (Real-Time)" : p.store}</span>
                    <span className="ps-status">
                      {p.status === "LIVE" ? "Live API" : p.status === "CONFIG_REQUIRED" ? "API Key Needed" : "Unavailable"}
                    </span>
                  </div>
                ))}
            </div>
          </div>
        )}

        {/* Content area */}
        {loading ? (
          <div className="results-loading">
            <div className="loading-spinner-box">
              <FaSpinner className="spinner-icon" />
              <p>Querying Amazon and Flipkart for live prices...</p>
            </div>
            <div className="skeleton-grid">
              {[1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} className="skeleton-card">
                  <div className="skeleton-img"></div>
                  <div className="skeleton-line full"></div>
                  <div className="skeleton-line half"></div>
                  <div className="skeleton-box"></div>
                </div>
              ))}
            </div>
          </div>
        ) : error ? (
          <div className="results-error">
            <FaExclamationCircle className="error-icon" />
            <h3>Unable to retrieve products</h3>
            <p>{error}</p>
            <button className="retry-btn" onClick={() => fetchResults(query)}>
              Retry Search
            </button>
          </div>
        ) : searchData.results?.length === 0 ? (
          <div className="results-empty">
            <span className="empty-emoji">🔍</span>
            <h3>No products found matching "{query}"</h3>
            <p>
              Try searching for popular electronics like <strong>MacBook Air M2</strong>, <strong>iPhone 15</strong>, <strong>Galaxy S24</strong>, or <strong>Laptop</strong>.
            </p>
            <div className="empty-quick-links">
              <Link to="/results?q=MacBook" className="eq-link">MacBook Deals</Link>
              <Link to="/results?q=iPhone" className="eq-link">iPhone Deals</Link>
              <Link to="/results?q=Samsung" className="eq-link">Samsung Deals</Link>
              <Link to="/results?q=Laptop" className="eq-link">All Laptops</Link>
            </div>
          </div>
        ) : (
          <div className="results-content">
            <div className="results-meta-bar">
              <span className="results-count">
                Found <strong>{searchData.results.length}</strong> product{searchData.results.length > 1 ? "s" : ""} with Amazon &amp; Flipkart comparison
              </span>
            </div>

            <div className="product-grid">
              {searchData.results.map((product) => (
                <Productcard key={product.productId} product={product} />
              ))}
            </div>
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
}

export default Results;