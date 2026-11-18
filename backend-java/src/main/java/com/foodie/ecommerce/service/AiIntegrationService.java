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

    public RecommendationResponse getRecommendations(Long userId, int topN) {
        try {
            return restClient.get()
                    .uri("/get-recommendations/{userId}?top_n={topN}", userId, topN)
                    .retrieve()
                    .body(RecommendationResponse.class);
        } catch (Exception ex) {
            return new RecommendationResponse(
                    userId,
                    "fallback_offline_ml",
                    List.of("Swahili Beef Pilau", "Grilled Tilapia & Ugali", "Fresh Mango Passion Smoothie")
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
            return new ChurnPredictionResponse(
                    userId,
                    0.10,
                    "Low",
                    "Fallback: Standard Weekly Menu Highlight"
            );
        }
    }
}
