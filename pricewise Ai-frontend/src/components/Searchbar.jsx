import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { FaSearch, FaTimes } from "react-icons/fa";
import "../styles/searchbar.css";

function Searchbar({ initialQuery = "" }) {
  const [query, setQuery] = useState(initialQuery);
  const navigate = useNavigate();

  const handleSearch = (e) => {
    if (e) e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    navigate(`/results?q=${encodeURIComponent(trimmed)}`);
  };

  const handleQuickSearch = (term) => {
    setQuery(term);
    navigate(`/results?q=${encodeURIComponent(term)}`);
  };

  return (
    <div className="search-container">
      <form className="search-box" onSubmit={handleSearch}>
        <div className="input-box">
          <FaSearch className="search-icon" />
          <input
            type="text"
            placeholder="Search products (e.g. MacBook Air M2, iPhone 15, Laptop)..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query && (
            <button type="button" className="clear-btn" onClick={() => setQuery("")}>
              <FaTimes />
            </button>
          )}
        </div>

        <button type="submit" className="search-btn">
          Compare Prices
        </button>
      </form>

      <div className="quick-tags">
        <span className="quick-label">Trending:</span>
        <button type="button" onClick={() => handleQuickSearch("MacBook Air M2")}>MacBook Air M2</button>
        <button type="button" onClick={() => handleQuickSearch("iPhone 15")}>iPhone 15</button>
        <button type="button" onClick={() => handleQuickSearch("Samsung Galaxy S24")}>Galaxy S24</button>
        <button type="button" onClick={() => handleQuickSearch("Laptop")}>Laptops</button>
      </div>
    </div>
  );
}

export default Searchbar;