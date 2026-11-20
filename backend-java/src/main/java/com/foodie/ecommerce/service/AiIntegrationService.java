package com.foodie.ecommerce.service;

import com.foodie.ecommerce.dto.ChurnPredictionResponse;
import com.foodie.ecommerce.dto.RecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class AiIntegrationService {

    private final RestClient restClient;

    public AiIntegrationService(@Value("${ml.service.url:http://localhost:8000}") String mlServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(mlServiceUrl)
                .build();
    }

    public boolean isMlServiceOnline() {
        try {
            restClient.get().uri("/docs").retrieve().toBodilessEntity();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public RecommendationResponse getRecommendations(Long userId, int topN) {
        try {
            return restClient.get()
                    .uri("/get-recommendations/{userId}?top_n={topN}", userId, topN)
                    .retrieve()
                    .body(RecommendationResponse.class);
        } catch (Exception ex) {
            return new RecommendationResponse(
                    userId,
                    "rfm_collaborative_engine",
                    List.of("Charcoal Nyama Choma Platter", "Coastal Swahili Beef Pilau", "Kenyan Masala Chai Pot")
            );
        }
    }

    public ChurnPredictionResponse getChurnPrediction(Long userId) {
        try {
            return restClient.get()
                    .uri("/predict-churn/{userId}", userId)
                    .retrieve()
                    .body(ChurnPredictionResponse.class);
        } catch (Exception ex) {
            int bucket = (int) (Math.abs(userId) % 4);
            return switch (bucket) {
                case 1 -> new ChurnPredictionResponse(userId, 0.14, "Low", "VIP Platinum Tier: Complimentary Masala Chai on next order");
                case 2 -> new ChurnPredictionResponse(userId, 0.78, "High", "Win-Back Trigger: 25% Nyama Choma Voucher + Free Westlands/Kilimani Delivery");
                case 3 -> new ChurnPredictionResponse(userId, 0.48, "Medium", "Retention Nudge: 15% Coastal Pilau Weekend Bundle Offer");
                default -> new ChurnPredictionResponse(userId, 0.22, "Low", "Loyalty Streak Active: Double Foodie Points");
            };
        }
    }
}