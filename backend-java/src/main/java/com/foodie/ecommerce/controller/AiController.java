package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.dto.ChurnPredictionResponse;
import com.foodie.ecommerce.dto.RecommendationResponse;
import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.repository.ProductRepository;
import com.foodie.ecommerce.service.AiIntegrationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final AiIntegrationService aiIntegrationService;
    private final ProductRepository productRepository;

    public AiController(AiIntegrationService aiIntegrationService, ProductRepository productRepository) {
        this.aiIntegrationService = aiIntegrationService;
        this.productRepository = productRepository;
    }

    @GetMapping("/recommendations/{userId}")
    public Map<String, Object> getPersonalizedMenu(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "3") int topN) {
        RecommendationResponse aiRecs = aiIntegrationService.getRecommendations(userId, topN);
        Set<String> recommendedNames = Set.copyOf(aiRecs.getRecommendedItems());

        List<Product> matchedProducts = productRepository.findAll().stream()
                .filter(p -> recommendedNames.contains(p.getName()))
                .collect(Collectors.toList());

        return Map.of(
                "userId", userId,
                "strategy", aiRecs.getStrategy(),
                "recommendedNames", aiRecs.getRecommendedItems(),
                "matchedCatalogProducts", matchedProducts
        );
    }

    @GetMapping("/churn/{userId}")
    public ChurnPredictionResponse getCustomerChurnRisk(@PathVariable Long userId) {
        return aiIntegrationService.getChurnPrediction(userId);
    }
}
