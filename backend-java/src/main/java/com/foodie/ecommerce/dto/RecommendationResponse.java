package com.foodie.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    @JsonProperty("user_id")
    private Long userId;

    private String strategy;

    @JsonProperty("recommended_items")
    private List<String> recommendedItems;
}
