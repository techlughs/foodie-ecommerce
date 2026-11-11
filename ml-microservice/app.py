from fastapi import FastAPI

app = FastAPI(title="Foodie eCommerce AI Engine")

@app.get("/")
def home():
    return {
        "status": "AI Microservices Online",
        "modules": ["Churn Prediction", "Recommendations"]
    }

@app.get("/predict-churn/{user_id}")
def predict_churn(user_id: int):
    # Skeleton churn endpoint
    return {
        "user_id": user_id,
        "churn_risk_score": 0.18,
        "risk_level": "Low",
        "recommended_action": "Standard Promo"
    }

@app.get("/get-recommendations/{user_id}")
def get_recommendations(user_id: int):
    # Skeleton recommendation endpoint
    return {
        "user_id": user_id,
        "recommended_item_ids": ["FOOD_101", "FOOD_102", "FOOD_103"]
    }