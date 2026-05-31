package com.nutricard.dto;

import java.util.Map;

public record CompareResponse(
        FoodSummary foodA,
        FoodSummary foodB,
        Map<String, String> winner
) {
    public record FoodSummary(
            Long id,
            String name,
            String role,
            Map<String, Double> scores
    ) {}
}
