package com.nutricard.dto;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;

import java.util.List;

// GET /foods response shape: all Food fields flattened (backwards compatible with the
// previous raw-entity response) plus derived badges and the overall rating (null while a
// food's score hasn't been computed yet).
public record FoodListItem(Long id, String name, String category, String description,
                           Integer servingSizeG, String foodRole, List<Badge> badges,
                           Double overallScore) {

    public static FoodListItem of(Food food, List<Badge> badges, NutritionScore score) {
        return new FoodListItem(food.getId(), food.getName(), food.getCategory(),
                food.getDescription(), food.getServingSizeG(), food.getFoodRole().name(), badges,
                score == null ? null : score.getOverallScore());
    }
}
