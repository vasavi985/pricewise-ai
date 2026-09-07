import React from "react";
import Navbar from "../components/Navbar";
import Hero from "../components/Hero";
import Searchbar from "../components/Searchbar";
import StoreLogos from "../components/StoreLogos";
import Features from "../components/Features";
import Footer from "../components/Footer";
import "../styles/home.css";

function Home() {
  return (
    <div className="home-page">
      <Navbar />
      <main>
        <Hero />
        <Searchbar />
        <StoreLogos />
        <Features />
      </main>
      <Footer />
    </div>
  );
}

export default Home;