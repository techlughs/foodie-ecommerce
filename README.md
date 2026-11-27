# Foodie eCommerce Platform

Full-stack food ordering storefront, kitchen dispatch portal, and machine learning service built with Spring Boot 3 (Java 21), Thymeleaf, MySQL, MongoDB, and Python (FastAPI + scikit-learn). The platform models a Nairobi kitchen delivery service with polyglot persistence, live order tracking, automated retention offers, and role-based access control.

---

## Architecture Overview

The system is split into two services communicating over REST:

1. **Core Web Application (`backend-java`)**
   * Built with Spring Boot 3 and Java 21, serving server-rendered Thymeleaf views (`index.html`, `admin.html`) and JSON REST endpoints (`/api/**`).
   * **MySQL (`foodecommerce_db`)**: Stores transactional records—users, orders, and order line items—via Spring Data JPA.
   * **MongoDB (`foodecommerce_nosql`)**: Stores the dynamic dish catalog (`products`) and behavioral telemetry (`clickstream_logs`) via Spring Data MongoDB.
   * **Automatic Data Seeding**: `DataSeeder` initializes the default admin account (`admin@foodie.co.ke`) and populates the initial Nairobi dish catalog on startup if the catalog is empty.

2. **AI & Analytics Microservice (`ml-microservice`)**
   * Built with FastAPI, pandas, and scikit-learn, running on port `8000`.
   * **Content-Based Dish Recommender**: Uses `TfidfVectorizer` and cosine similarity over dish names, categories, descriptions, and ingredients to return related meal suggestions.
   * **Customer Churn Predictor**: Uses a `RandomForestClassifier` trained on order recency (`days_since_last_order`), 30-day order frequency (`orders_last_30d`), average order value (`avg_order_value`), menu views (`menu_views_last_14d`), and cart abandonment rate (`cart_abandonment_rate`) to score churn risk and trigger checkout retention incentives.

---

## Project Structure

```text
foodie-ecommerce/
├── backend-java/
│   ├── src/main/java/com/foodie/ecommerce/
│   │   ├── config/          # DataSeeder and WebConfig (static upload resource mapping)
│   │   ├── controller/      # StorefrontController, PortalApiController, ProductController, OrderController, AiController
│   │   ├── dto/             # Request and ML prediction response payloads
│   │   ├── model/           # JPA entities (User, Order, OrderItem) & Mongo documents (Product, ClickstreamLog)
│   │   ├── repository/      # Spring Data JPA and MongoDB repositories
│   │   └── service/         # AiIntegrationService (timeout-guarded REST bridge to FastAPI)
│   ├── src/main/resources/
│   │   ├── templates/       # index.html (customer storefront) & admin.html (kitchen/admin studio)
│   │   └── application.properties.example
│   └── pom.xml
├── database/
│   ├── schema.sql           # MySQL DDL table definitions
│   └── mongodb_schema.js    # MongoDB $jsonSchema validators and collection indexes
├── docs/
│   └── Capstone_Project_Report.md
├── ml-microservice/
│   ├── data/                # Clickstream and user metrics datasets
│   ├── models/              # Serialized joblib models (recommender.joblib, churn_rf.joblib)
│   ├── app.py               # FastAPI server, model training, validation metrics, and inference routes
│   ├── generate_data.py     # Synthetic dataset generator
│   └── requirements.txt
└── README.md
```

---

## Prerequisites

* **Java Development Kit (JDK) 21**
* **Maven 3.9+** (or the included `./mvnw` / `mvnw.cmd` wrapper)
* **MySQL 8.0+** running locally on port `3306`
* **MongoDB** (local instance or MongoDB Atlas cluster URI)
* **Python 3.11+**

---

## Setup & Running Locally

### 1. Start the Python ML Microservice

```bash
cd ml-microservice
python -m venv venv

# Windows (PowerShell)
.\venv\Scripts\Activate.ps1
# macOS / Linux
source venv/bin/activate

pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

The FastAPI service runs at `http://localhost:8000` (interactive API docs at `http://localhost:8000/docs`).

### 2. Configure the Spring Boot Backend

Copy the example properties file and add your local MySQL and MongoDB credentials:

```bash
cd backend-java/src/main/resources
cp application.properties.example application.properties
```

Update `application.properties`:

```properties
server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/foodecommerce_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=YOUR_MYSQL_USERNAME
spring.datasource.password=YOUR_MYSQL_PASSWORD

spring.data.mongodb.uri=mongodb+srv://YOUR_MONGO_USER:YOUR_MONGO_PASSWORD@YOUR_CLUSTER.mongodb.net/foodecommerce_nosql?retryWrites=true&w=majority

ml.service.url=http://localhost:8000
```

Initialize the relational tables with `database/schema.sql` and apply MongoDB collection validators using `database/mongodb_schema.js`.

### 3. Run the Spring Boot Application

```bash
cd backend-java
./mvnw spring-boot:run
```

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`.

---

## Application Routes & Demo Credentials

* **Customer Storefront**: `http://localhost:8080/store` (root `/` redirects here)
  * Supports guest browsing, tray management, account registration/sign-in with password visibility toggles, self-service account deletion, Nairobi delivery zone selection, live order stage tracking, and automatic churn-prevention offers at checkout.
* **Kitchen & Admin Studio**: `http://localhost:8080/admin/churn`
  * **Admin Email**: `admin@foodie.co.ke`
  * **Admin Password**: `admin123`
  * Provides live order dispatch controls (`Accept Order`, `Dispatch Rider`, `Mark Delivered`), KPI monitoring, dish catalog CRUD with image uploads, churn risk scoring, model validation metrics, and recent clickstream activity logs.

---

## Key API Endpoints

### Spring Boot (`http://localhost:8080`)
- `GET /store` — Customer storefront page
- `GET /admin/churn` — Admin & kitchen operations dashboard
- `GET /api/products` — List all dishes from MongoDB
- `POST /api/orders` — Submit a new customer order to MySQL
- `GET /api/orders/user/{userId}` — Poll live order status updates for a customer
- `POST /api/portal/admin/orders/{orderId}/status` — Advance order status without page reload
- `GET /api/portal/admin/live-metrics` — Stream live revenue, category counts, and per-user churn risk scores
- `POST /api/portal/admin/retention/{userId}` — Dispatch a targeted retention incentive to an at-risk user
- `DELETE /api/portal/user/{id}` — Permanently delete a customer account and associated history
- `GET /api/ai/metrics` — Retrieve holdout validation metrics from the ML microservice
- `GET /api/ai/recommendations/{userId}` — Fetch personalized dish recommendations via the ML service
- `GET /api/ai/churn/{userId}` — Evaluate customer churn probability and recommended retention action

### FastAPI Microservice (`http://localhost:8000`)
- `GET /` — Health check
- `GET /metrics` — Expose model validation metrics (Accuracy, ROC-AUC, Precision, Recall, Precision@3)
- `GET /get-recommendations/{user_id}` — Return top-N similar dishes using TF-IDF cosine similarity
- `GET /predict-churn/{user_id}` — Predict churn probability from stored user metrics
- `POST /predict-churn/live` — Predict churn probability in real time from live behavioral features
