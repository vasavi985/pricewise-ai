import React from "react";
import "../styles/hero.css";

function Hero() {
  return (
    <section className="hero">
      <div className="hero-badge">
        ✨ Real Price Intelligence &amp; Drop Tracking
      </div>

      <h1 className="hero-title">
        Find the Best Price.
        <br />
        <span>Track It. Save More.</span>
      </h1>

      <p className="hero-description">
        Search any product across Amazon, Flipkart, Croma, and live commerce.
        <br />
        PriceWise AI tracks historical prices over time, highlights the lowest deal, and notifies you the second prices drop.
      </p>
    </section>
  );
}

export default Hero;