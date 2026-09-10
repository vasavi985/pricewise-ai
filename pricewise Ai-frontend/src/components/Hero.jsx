import React from "react";
import "../styles/hero.css";

function Hero() {
  return (
    <section className="hero">
      <div className="hero-badge">
        ✨ Real Amazon vs Flipkart Price Comparison
      </div>

      <h1 className="hero-title">
        Compare Amazon &amp; Flipkart.
        <br />
        <span>Find the Lowest Price.</span>
      </h1>

      <p className="hero-description">
        Search any product and see real-time prices directly from Amazon and Flipkart side-by-side.
        <br />
        PriceWise AI identifies the cheaper store, calculates your exact savings, and lets you track price drops.
      </p>
    </section>
  );
}

export default Hero;