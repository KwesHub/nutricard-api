package com.nutricard.dto;

import java.util.List;
import java.util.Map;

public record CompareResponse(
        FoodSummary foodA,
        FoodSummary foodB,
        Map<String, String> winner,
        Map<String, List<UniqueNutrient>> uniqueStrengths
) {
    public record FoodSummary(
            Long id,
            String name,
            String role,
            Map<String, Double> scores
    ) {}

    public record UniqueNutrient(
            String name,
            double pctRda,
            boolean rare
    ) {}
}
