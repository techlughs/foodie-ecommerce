package com.foodie.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPredictionResponse {
    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("churn_risk_score")
    private Double churnRiskScore;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("recommended_action")
    private String recommendedAction;
}
