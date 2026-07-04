package com.nutricard.dto;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FoodCardResponse {
    private Food food;
    private NutritionScore nutritionScore;
    private CardInsights insights;

    public record CardInsights(String standoutFact, String penaltyNote) {}
}
