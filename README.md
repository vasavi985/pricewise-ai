# 🛒 PriceWise AI

### Amazon & Flipkart Price Comparison

PriceWise AI is a full-stack web application that allows users to search for products and compare prices between **Amazon and Flipkart** in one place.

🌐 **Live Website:** https://pricewise-ai-be26e.web.app/

---

## ✨ Features

- 🔍 Search for products
- 🛒 Compare Amazon and Flipkart prices
- 💰 Find the lowest available price
- 🔗 Open the original product page
- 🖼️ View product images and details
- 📈 Track product prices
- 🕘 User-specific search history
- 🔐 Firebase Authentication
- ☁️ Firebase Firestore database
- ⚡ Parallel API searches
- 📱 Responsive user interface

---

## 🛠️ Tech Stack

### Frontend
- React
- Vite
- React Router
- Axios
- Recharts
- Firebase Authentication

### Backend
- Java 21
- Spring Boot
- REST APIs
- Firebase Admin SDK
- Firebase Firestore
- Maven

### APIs & Deployment
- Amazon API
- Flipkart API
- Firebase Hosting
- Render

---

## 🏗️ System Architecture

```text
              ┌──────────────────┐
              │   React Frontend  │
              │ Firebase Hosting  │
              └────────┬─────────┘
                       │
                    REST API
                       │
                       ▼
              ┌──────────────────┐
              │  Spring Boot API │
              │     Render       │
              └────────┬─────────┘
                       │
              ┌────────┴────────┐
              │                 │
              ▼                 ▼
        ┌──────────┐      ┌──────────┐
        │  Amazon  │      │ Flipkart │
        │   API    │      │   API    │
        └────┬─────┘      └────┬─────┘
             │                 │
             └────────┬────────┘
                      ▼
              Price Comparison


🔗 Links

🌐 Live Website: https://pricewise-ai-be26e.web.app/

💻 GitHub: https://github.com/vasavi985/pricewise-ai
                      │
                      ▼
             Firebase Firestore
