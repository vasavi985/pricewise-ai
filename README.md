# PriceWise AI — Production-Style Price Tracking & Intelligence Platform

PriceWise AI is a full-stack price comparison and price intelligence application. It retrieves real-time product prices across supported retailers, tracks price fluctuations over time, maintains immutable price history records, and triggers automated background alerts when prices drop.

---

## 🏗️ System Architecture

```
                               ┌────────────────────────┐
                               │   React 19 + Vite UI   │
                               │  (Port 5173 / Recharts)│
                               └───────────┬────────────┘
                                           │ HTTP / JSON
                                           ▼
                               ┌────────────────────────┐
                               │  Spring Boot REST API  │
                               │      (Port 8080)       │
                               └───────────┬────────────┘
                                           │
         ┌─────────────────────────────────┼─────────────────────────────────┐
         │                                 │                                 │
         ▼                                 ▼                                 ▼
┌──────────────────┐             ┌──────────────────┐              ┌──────────────────┐
│  Provider Layer  │             │ Scheduler & Drop │              │  MySQL Database  │
│  (PriceProvider) │             │    Detection     │              │  (pricewise_db)  │
└────────┬─────────┘             └────────┬─────────┘              └──────────────────┘
         │                                │
 ┌───────┴───────┬──────────────┬─────────┴─────────┐
 │               │              │                   │
 ▼               ▼              ▼                   ▼
Amazon PA-API  Flipkart API   Croma Partner   Live Commerce
(CONFIG_REQ)   (CONFIG_REQ)   (UNAVAILABLE)   (LIVE / Active)
```

---

## 🛒 Supported Store Integrations

| Store | Status | Description | Required Configuration |
| :--- | :--- | :--- | :--- |
| **Catalog DB** | `SAMPLE_DATA` | Verified internal catalog (MacBook, iPhone, Galaxy) | Built-in MySQL |
| **Open Commerce** | `LIVE` | Real-time global commerce & product API | Built-in (No key required) |
| **Amazon India** | `CONFIG_REQUIRED` | Amazon Product Advertising API (PA-API 5.0) | `AMAZON_ACCESS_KEY`, `AMAZON_SECRET_KEY`, `AMAZON_PARTNER_TAG` |
| **Flipkart** | `CONFIG_REQUIRED` | Official Flipkart Affiliate API | `FLIPKART_AFFILIATE_ID`, `FLIPKART_AFFILIATE_TOKEN` |
| **Croma** | `UNAVAILABLE` | Enterprise commercial partner gateway | `CROMA_API_KEY` |

> [!NOTE]
> PriceWise AI strictly adheres to retailer terms of service, robots.txt, and rate limits. Unconfigured stores display transparent status badges rather than fabricated prices.

---

## 🗄️ Database Schema (`pricewise_db`)

The database uses an append-only relational design so historical price records are never overwritten:

1. **`products`**: Canonical product entities (`id`, `canonical_name`, `brand`, `model`, `category`, `description`, `image_url`, `rating`).
2. **`store_products`**: Specific retailer listings (`id`, `product_id`, `store`, `store_product_id`, `current_price`, `currency`, `availability`, `status`, `product_url`, `last_checked_at`).
3. **`price_records`**: Immutable historical price log (`id`, `store_product_id`, `price`, `currency`, `availability`, `checked_at`, `source`).
4. **`tracked_products`**: User price tracking configurations (`id`, `store_product_id`, `user_id`, `user_email`, `target_price`, `target_drop_percentage`, `initial_price`, `last_notified_price`, `active`, `last_checked_at`).
5. **`notification_records`**: Audit log of sent and logged price drop alerts (`id`, `tracked_product_id`, `recipient_email`, `previous_price`, `new_price`, `drop_amount`, `drop_percentage`, `status`, `sent_at`).

---

## ⚡ Background Price Tracking & Scheduler

- **Scheduler**: Powered by Spring's `@Scheduled` background worker (`PriceTrackingScheduler.java`).
- **Interval**: Configurable via `pricewise.tracking.interval` (default: 60,000 ms / 60 seconds).
- **Evaluation**: For each active tracked product, the scheduler asks the respective provider for the latest price, appends a `PriceRecord`, detects drops below the user's target price or discount threshold, and dispatches email alerts via `NotificationService` (or logs them if SMTP is unconfigured).

---

## 🚀 Getting Started (Running Locally on Windows)

### Prerequisites
- **Java 21** (`java -version`)
- **Node.js 18+** & `npm`
- **MySQL 8.0 Server** running locally on port 3306

### 1. Database Setup
Create database `pricewise_db` in MySQL:
```sql
CREATE DATABASE IF NOT EXISTS pricewise_db;
```

### 2. Configure Environment Variables
Copy `.env.example` to `.env` or set environment variables:
```bash
# In project root:
copy .env.example .env
```
Ensure your MySQL password is set in the `DB_PASSWORD` environment variable or in your local `.env` file (see `.env.example`).

### 3. Run Backend (Spring Boot)
```powershell
cd "d:\vasu\Projects\Pricewise Ai\backend\backend"
.\mvnw.cmd spring-boot:run
```
Backend will start on: **`http://localhost:8080`**

### 4. Run Frontend (React + Vite)
```powershell
cd "d:\vasu\Projects\Pricewise Ai\pricewise Ai-frontend"
npm install
npm run dev
```
Frontend will be available at: **`http://localhost:5173`**

---

## 📡 REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/products/search?query={q}` | Multi-provider unified product search with price comparison |
| `GET` | `/api/products/{id}` | Detailed product information with store listings and AI buy recommendation |
| `GET` | `/api/products/{id}/history` | Historical price points and time-series data for Recharts |
| `GET` | `/api/products/providers` | Status and requirements for all connected store integrations |
| `POST` | `/api/tracking` | Track a product listing (`storeProductId`, `targetPrice`, `userEmail`) |
| `GET` | `/api/tracking` | List all active tracked items for the user |
| `DELETE`| `/api/tracking/{id}` | Stop tracking a product |
| `POST` | `/api/tracking/{id}/check-now` | Trigger an immediate manual price check for a tracked item |

---

## 🔌 How to Add Another Store Provider

1. Implement the `PriceProvider` interface in `com.pricewise.backend.provider`:
   ```java
   @Component
   public class TargetPriceProvider implements PriceProvider {
       @Override public String getStoreName() { return "TARGET"; }
       @Override public boolean isConfigured() { return true; }
       @Override public String getStoreStatus() { return "LIVE"; }
       @Override public List<ProviderProductDTO> searchProducts(String query) { ... }
       @Override public ProviderProductDTO fetchCurrentPrice(String storeProductId, String url) { ... }
   }
   ```
2. The `ProviderManager` automatically detects all Spring beans implementing `PriceProvider` via dependency injection and incorporates them into search, comparison, and scheduling with zero changes to existing controllers.
