# Foodie eCommerce Platform

Full-stack food ordering storefront, kitchen dispatch portal, and machine learning service built with Spring Boot (Java 21), Thymeleaf, MySQL, MongoDB, and Python (FastAPI + scikit-learn). The platform models a Nairobi kitchen delivery service with polyglot persistence, live order tracking, automated retention offers, and role-based access control.

---

## Architecture Overview

The system is split into two services communicating over REST:

1. **Core Web Application (`backend-java`)**
   - Built with Spring Boot 4 and Java 21, serving server-rendered Thymeleaf views (`index.html`, `admin.html`) and JSON REST endpoints (`/api/**`).
   - **MySQL (`foodecommerce_db`)**: Stores transactional records—users, orders, order line items, and clickstream interaction logs—via Spring Data JPA.
   - **MongoDB (`foodecommerce_catalog`)**: Stores the dynamic dish catalog (names, descriptions, categories, pricing in KES, tags, and uploaded dish imagery) via Spring Data MongoDB.
   - **Automatic Data Seeding**: `DataSeeder` initializes the default admin account (`admin@foodie.co.ke`) and populates the initial Nairobi dish catalog on startup if the catalog is empty.

2. **AI & Analytics Microservice (`ml-microservice`)**
   - Built with FastAPI, pandas, and scikit-learn, running on port `8000`.
   - **Content-Based Dish Recommender**: Uses `TfidfVectorizer` and cosine similarity over dish categories and flavor/dietary tags to return related meal suggestions.
   - **Customer Churn Predictor**: Uses a `RandomForestClassifier` trained on order recency (`days_since_last_order`), 30-day order frequency (`orders_last_30d`), average order value (`avg_order_value_kes`), and cart abandonment rate (`cart_abandonment_rate`) to score churn risk and trigger checkout retention incentives.

---

## Project Structure

    foodie-ecommerce/
    ├── backend-java/
    │   ├── src/main/java/com/foodie/ecommerce/
    │   │   ├── config/          # DataSeeder and WebConfig (static upload resource mapping)
    │   │   ├── controller/      # StorefrontController, PortalApiController, ProductController, OrderController, AiController
    │   │   ├── dto/             # Request and ML prediction response payloads
    │   │   ├── model/           # JPA entities (User, Order, OrderItem, ClickstreamLog) & Mongo document (Product)
    │   │   ├── repository/      # Spring Data JPA and MongoDB repositories
    │   │   └── service/         # AiIntegrationService (REST bridge to FastAPI)
    │   ├── src/main/resources/
    │   │   ├── templates/       # index.html (customer storefront) & admin.html (kitchen/admin studio)
    │   │   └── application.properties.example
    │   └── pom.xml
    ├── database/
    │   └── schema.sql           # MySQL table definitions
    ├── ml-microservice/
    │   ├── data/                # Clickstream and user metrics datasets
    │   ├── models/              # Serialized joblib models (recommender.joblib, churn_rf.joblib)
    │   ├── app.py               # FastAPI server and model training/inference endpoints
    │   ├── generate_data.py     # Synthetic dataset generator
    │   └── requirements.txt
    └── README.md

---

## Prerequisites

- **Java Development Kit (JDK) 21**
- **Maven 3.9+** (or the included `./mvnw` / `mvnw.cmd` wrapper)
- **MySQL 8.0+** running locally on port `3306`
- **MongoDB** (local instance or MongoDB Atlas cluster URI)
- **Python 3.11+**

---

## Setup & Running Locally

### 1. Start the Python ML Microservice

    cd ml-microservice
    python -m venv venv

    # Windows (PowerShell)
    .\venv\Scripts\Activate.ps1
    # macOS / Linux
    source venv/bin/activate

    pip install -r requirements.txt
    uvicorn app:app --host 0.0.0.0 --port 8000 --reload

The FastAPI service runs at `http://localhost:8000` (interactive API docs at `http://localhost:8000/docs`).

### 2. Configure the Spring Boot Backend

Copy the example properties file and add your local MySQL and MongoDB credentials:

    cd backend-java/src/main/resources
    cp application.properties.example application.properties

Update `application.properties`:

    server.port=8080

    spring.datasource.url=jdbc:mysql://localhost:3306/foodecommerce_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
    spring.datasource.username=YOUR_MYSQL_USERNAME
    spring.datasource.password=YOUR_MYSQL_PASSWORD

    spring.data.mongodb.uri=mongodb+srv://YOUR_MONGO_USER:YOUR_MONGO_PASSWORD@YOUR_CLUSTER.mongodb.net/foodecommerce_catalog?retryWrites=true&w=majority

    ml.service.url=http://localhost:8000

You can also initialize the MySQL schema manually using `database/schema.sql`; otherwise Hibernate (`ddl-auto=update`) creates the tables automatically on first boot.

### 3. Run the Spring Boot Application

    cd backend-java
    ./mvnw spring-boot:run

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`.

---

## Application Routes & Demo Credentials

- **Customer Storefront**: `http://localhost:8080/store` (root `/` redirects here)
  - Supports guest browsing, tray management, account registration/sign-in, Nairobi delivery zone selection, live order stage tracking, and automatic churn-prevention offers at checkout.
- **Kitchen & Admin Studio**: `http://localhost:8080/admin/churn`
  - **Admin Email**: `admin@foodie.co.ke`
  - **Admin Password**: `admin123`
  - Provides live order dispatch controls (`Accept Order`, `Dispatch Rider`, `Mark Delivered`), KPI monitoring, dish catalog CRUD with image uploads, churn risk scoring, and recent clickstream activity logs.

---

## Key API Endpoints

### Spring Boot (`http://localhost:8080`)
- `GET /store` — Customer storefront page
- `GET /admin/churn` — Admin & kitchen operations dashboard
- `GET /api/products` — List all dishes from MongoDB
- `POST /api/orders` — Submit a new customer order to MySQL
- `POST /api/portal/admin/orders/{id}/status` — Advance order status without page reload
- `GET /api/ai/recommend/{productName}` — Fetch dish recommendations via the ML service
- `POST /api/ai/churn` — Evaluate user churn probability and retention offer

### FastAPI Microservice (`http://localhost:8000`)
- `GET /` — Health check
- `GET /recommend/{product_name}` — Return top-N similar dishes using TF-IDF cosine similarity
- `POST /predict-churn` — Predict churn probability from customer behavioral metrics