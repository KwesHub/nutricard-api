package com.nutricard.dto;

import com.nutricard.model.Meal;
import com.nutricard.model.MealFood;
import com.nutricard.model.MealScore;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class MealCardResponse {
    private Meal meal;
    private MealScore mealScore;
    private List<MealFood> foods;
    private NutrientAnalysis nutrientAnalysis;

    public record NutrientAnalysis(
            Map<String, Double> coverage,
            List<Gap> gaps,
            List<Suggestion> suggestions
    ) {
        public record Gap(String name, boolean rare) {}

        public record Suggestion(Long foodId, String foodName, List<String> covers) {}
    }
}
