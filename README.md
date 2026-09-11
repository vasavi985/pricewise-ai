# PriceWise AI 🛒

### Compare product prices across Amazon and Flipkart

PriceWise AI is a full-stack product price comparison and tracking application that helps users search for products and compare prices from **Amazon and Flipkart** using real marketplace API data.

Users can search for products, view product details, compare available prices, track products, and manage their search history through a secure authenticated account.

---

## 🌐 Live Application

### 🚀 Live Website
https://pricewise-ai-be26e.web.app/

### ⚙️ Backend API
https://pricewise-ai-4vy1.onrender.com/

### 💻 GitHub Repository
https://github.com/vasavi985/pricewise-ai

---

## ✨ Features

### 🔍 Product Search

- Search for products by name.
- Retrieves real product information from Amazon and Flipkart.
- Displays product title, image, price, rating, availability, and product links when provided by the APIs.

### 💰 Price Comparison

- Compare product prices across Amazon and Flipkart.
- Displays available marketplace results.
- Helps users identify potentially better prices.
- Shows savings when comparable products are available from multiple stores.

### 🛍️ Amazon Integration

- Retrieves real Amazon product and pricing data.
- Uses the Amazon API through RapidAPI.
- Displays Amazon product information and marketplace links.

### 🔵 Flipkart Integration

- Retrieves real Flipkart product data using ReefAPI.
- Uses the ReefAPI Flipkart product search endpoint.
- Supports real product search and product information retrieval.

### 📈 Price Tracking

- Users can track products they are interested in.
- Tracked products are associated with the authenticated user.
- Users can view their tracked products and price information.

### 🔐 Firebase Authentication

- Email/password authentication.
- Secure user registration and login.
- Firebase ID token verification on the backend.
- User-specific data isolation.

### 🕘 Search History

- Stores authenticated users' search history.
- Users can view their previous searches.
- Users can clear their search history.

### ⚡ Performance Optimizations

- Concurrent marketplace provider searches.
- Backend in-memory caching.
- Firestore caching.
- Search result caching.
- Frontend API caching.
- Provider status caching.
- Backend warm-up support for Render.

### 📱 Responsive Interface

- Responsive product search interface.
- Product cards.
- Marketplace comparison cards.
- Product details pages.
- Price tracking interface.
- Authentication pages.

---

## 🏗️ Architecture

```text
                         PriceWise AI
                              │
                 ┌────────────┴────────────┐
                 │                         │
          Firebase Hosting               Render
                 │                         │
          React Frontend            Spring Boot Backend
                                           │
                              ┌────────────┴────────────┐
                              │                         │
                           Amazon                   Flipkart
                              │                         │
                        RapidAPI                  ReefAPI
                              │                         │
                              └────────────┬────────────┘
                                           │
                                      Firestore
