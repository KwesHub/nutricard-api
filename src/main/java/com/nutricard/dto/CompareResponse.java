package com.nutricard.dto;

import java.util.List;
import java.util.Map;

public record CompareResponse(
        FoodSummary foodA,
        FoodSummary foodB,
        Map<String, String> winner,
        Map<String, List<UniqueNutrient>> uniqueStrengths,
        Map<String, List<LeadingNutrient>> leadsOn
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

    // A nutrient both foods carry, but this food covers meaningfully more of —
    // complements uniqueStrengths, which only lists nutrients the other food barely has.
    public record LeadingNutrient(
            String name,
            double pctRda,
            double otherPctRda,
            boolean rare
    ) {}
}
