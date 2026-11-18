import os
import random
from datetime import datetime, timedelta
import numpy as np
import pandas as pd

random.seed(42)
np.random.seed(42)

os.makedirs("data", exist_ok=True)

PRODUCTS = [
    {"name": "Swahili Beef Pilau", "category": "Main Course", "price": 400.0, "tags": "beef rice spiced savory main"},
    {"name": "Grilled Tilapia & Ugali", "category": "Main Course", "price": 600.0, "tags": "fish grilled tilapia ugali healthy main"},
    {"name": "Fresh Mango Passion Smoothie", "category": "Beverages", "price": 250.0, "tags": "mango passion fruit cold sweet drink"},
    {"name": "Nyama Choma Platter", "category": "Main Course", "price": 850.0, "tags": "roasted beef grilled savory main"},
    {"name": "Chicken Biryani", "category": "Main Course", "price": 500.0, "tags": "chicken rice spiced aromatic main"},
    {"name": "Masala Chips", "category": "Sides", "price": 220.0, "tags": "potatoes spicy fries snack side"},
    {"name": "Beef Samosa Trio", "category": "Sides", "price": 180.0, "tags": "beef pastry crispy snack side"},
    {"name": "Tamarind Juice (Ukwaju)", "category": "Beverages", "price": 150.0, "tags": "tamarind tangy cold refreshing drink"},
    {"name": "Kenyan Masala Chai", "category": "Beverages", "price": 120.0, "tags": "tea spiced hot milk drink"},
    {"name": "Coconut Mahamri & Dip", "category": "Desserts", "price": 200.0, "tags": "coconut sweet pastry dessert"},
]

EVENT_WEIGHTS = {"VIEW": 1, "CART": 3, "ORDER": 5}
USER_IDS = list(range(1, 51))

clickstream_rows = []
base_time = datetime(2026, 11, 1, 12, 0, 0)

for user_id in USER_IDS:
    preferred_category = random.choice(["Main Course", "Beverages", "Sides", "Desserts"])
    num_events = random.randint(20, 40)

    for _ in range(num_events):
        if random.random() < 0.70:
            candidates = [p for p in PRODUCTS if p["category"] == preferred_category]
        else:
            candidates = PRODUCTS

        product = random.choice(candidates)
        event_type = random.choices(
            population=["VIEW", "CART", "ORDER"],
            weights=[0.60, 0.25, 0.15],
            k=1,
        )[0]

        timestamp = base_time + timedelta(
            days=random.randint(0, 16),
            hours=random.randint(0, 23),
            minutes=random.randint(0, 59),
        )

        clickstream_rows.append({
            "user_id": user_id,
            "product_name": product["name"],
            "category": product["category"],
            "tags": product["tags"],
            "price_kes": product["price"],
            "event_type": event_type,
            "weight": EVENT_WEIGHTS[event_type],
            "timestamp": timestamp.isoformat(),
        })

clickstream_df = pd.DataFrame(clickstream_rows)
clickstream_df.to_csv("data/clickstream.csv", index=False)

churn_rows = []
for user_id in USER_IDS:
    days_since_last_order = random.randint(1, 60)
    orders_last_30d = random.randint(0, 12) if days_since_last_order < 30 else 0
    avg_order_value_kes = round(random.uniform(250.0, 1400.0), 2)
    cart_abandonment_rate = round(random.uniform(0.05, 0.85), 2)

    churn_score = (
        (days_since_last_order / 60.0) * 0.50
        + (1 - min(orders_last_30d, 10) / 10.0) * 0.30
        + cart_abandonment_rate * 0.20
    )
    churned = 1 if (churn_score + np.random.normal(0, 0.05)) > 0.52 else 0

    churn_rows.append({
        "user_id": user_id,
        "days_since_last_order": days_since_last_order,
        "orders_last_30d": orders_last_30d,
        "avg_order_value_kes": avg_order_value_kes,
        "cart_abandonment_rate": cart_abandonment_rate,
        "churned": churned,
    })

churn_df = pd.DataFrame(churn_rows)
churn_df.to_csv("data/user_metrics.csv", index=False)

print(f"Generated {len(clickstream_df)} clickstream events -> data/clickstream.csv")
print(f"Generated {len(churn_df)} user churn profiles -> data/user_metrics.csv")
