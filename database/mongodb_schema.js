use foodecommerce_nosql;

db.createCollection("products", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["name", "price", "category", "available"],
      properties: {
        _id: { bsonType: ["objectId", "string"] },
        name: { bsonType: "string", minLength: 2, maxLength: 150 },
        description: { bsonType: ["string", "null"] },
        price: { bsonType: ["decimal", "double", "int", "long", "string"] },
        category: { bsonType: "string" },
        imageUrl: { bsonType: ["string", "null"] },
        ingredients: { bsonType: "array", items: { bsonType: "string" } },
        nutritionalInfo: { bsonType: ["object", "null"] },
        available: { bsonType: "bool" }
      }
    }
  },
  validationLevel: "moderate",
  validationAction: "error"
});

db.products.createIndex({ category: 1, available: 1 });
db.products.createIndex({ name: "text", description: "text", ingredients: "text" });

db.createCollection("clickstream_logs", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["userId", "productId", "eventType", "timestamp"],
      properties: {
        _id: { bsonType: ["objectId", "string"] },
        userId: { bsonType: ["long", "int"] },
        productId: { bsonType: "string" },
        productName: { bsonType: ["string", "null"] },
        eventType: { bsonType: "string", enum: ["VIEW", "ADD_TO_CART", "PURCHASE", "RECOMMENDATION_CLICK"] },
        timestamp: { bsonType: ["date", "string"] }
      }
    }
  },
  validationLevel: "moderate",
  validationAction: "error"
});

db.clickstream_logs.createIndex({ userId: 1, timestamp: -1 });
db.clickstream_logs.createIndex({ productId: 1, eventType: 1 });
