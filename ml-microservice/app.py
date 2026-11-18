import os
import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

app = FastAPI(
    title="Foodie eCommerce AI Engine",
    description="Hybrid Recommendation & Churn Prediction Microservice",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

os.makedirs("models", exist_ok=True)

clickstream_df = pd.read_csv("data/clickstream.csv")
user_metrics_df = pd.read_csv("data/user_metrics.csv")

catalog_df = (
    clickstream_df[["product_name", "category", "tags", "price_kes"]]
    .drop_duplicates(subset=["product_name"])
    .reset_index(drop=True)
)
catalog_df["soup"] = catalog_df["category"] + " " + catalog_df["tags"]

tfidf = TfidfVectorizer(stop_words="english")
tfidf_matrix = tfidf.fit_transform(catalog_df["soup"])
cosine_sim_matrix = cosine_similarity(tfidf_matrix, tfidf_matrix)

joblib.dump({"tfidf": tfidf, "catalog": catalog_df}, "models/recommender.joblib")

FEATURE_COLS = [
    "days_since_last_order",
    "orders_last_30d",
    "avg_order_value_kes",
    "cart_abandonment_rate",
]
X = user_metrics_df[FEATURE_COLS]
y = user_metrics_df["churned"]

churn_model = RandomForestClassifier(n_estimators=100, random_state=42)
churn_model.fit(X, y)
joblib.dump(churn_model, "models/churn_rf.joblib")


@app.get("/")
def home():
    return {
        "status": "AI Microservices Online",
        "models_loaded": ["TF-IDF Recommender", "RandomForest Churn Classifier"],
        "dataset_stats": {
            "clickstream_events": len(clickstream_df),
            "tracked_users": len(user_metrics_df),
            "catalog_items": len(catalog_df),
        },
    }


@app.get("/predict-churn/{user_id}")
def predict_churn(user_id: int):
    user_row = user_metrics_df[user_metrics_df["user_id"] == user_id]

    if user_row.empty:
        return {
            "user_id": user_id,
            "churn_risk_score": 0.15,
            "risk_level": "Low",
            "recommended_action": "Welcome Onboarding Offer",
        }

    features = user_row[FEATURE_COLS]
    churn_prob = float(churn_model.predict_proba(features)[0][1])

    if churn_prob >= 0.70:
        risk_level = "High"
        action = "Send 20% Comeback Voucher + Free Delivery"
    elif churn_prob >= 0.40:
        risk_level = "Medium"
        action = "Send 10% Loyalty Discount Push Notification"
    else:
        risk_level = "Low"
        action = "Standard Weekly Menu Highlight"

    return {
        "user_id": user_id,
        "churn_risk_score": round(churn_prob, 3),
        "risk_level": risk_level,
        "recommended_action": action,
    }


@app.get("/get-recommendations/{user_id}")
def get_recommendations(user_id: int, top_n: int = 3):
    user_events = clickstream_df[clickstream_df["user_id"] == user_id]

    if user_events.empty:
        popular = (
            clickstream_df.groupby("product_name")["weight"]
            .sum()
            .sort_values(ascending=False)
            .head(top_n)
            .index.tolist()
        )
        return {
            "user_id": user_id,
            "strategy": "popularity_fallback",
            "recommended_items": popular,
        }

    user_prefs = user_events.groupby("product_name")["weight"].sum()
    scores = np.zeros(len(catalog_df))

    for product_name, total_weight in user_prefs.items():
        matches = catalog_df.index[catalog_df["product_name"] == product_name].tolist()
        if matches:
            idx = matches[0]
            scores += cosine_sim_matrix[idx] * total_weight

    catalog_df_scored = catalog_df.copy()
    catalog_df_scored["score"] = scores
    top_dishes = (
        catalog_df_scored.sort_values(by="score", ascending=False)
        .head(top_n)["product_name"]
        .tolist()
    )

    return {
        "user_id": user_id,
        "strategy": "hybrid_tfidf_behavioral",
        "recommended_items": top_dishes,
    }
