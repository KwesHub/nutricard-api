package com.nutricard.dto;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;

import java.util.List;

// GET /foods response shape: all Food fields flattened (backwards compatible with the
// previous raw-entity response) plus derived badges and the overall rating (null while a
// food's score hasn't been computed yet).
public record FoodListItem(Long id, String name, String category, String description,
                           Integer servingSizeG, String foodRole, String frequency, List<Badge> badges,
                           Double overallScore, Stats stats) {

    // The five 0-100 stats shown on the grid card; null with the score while warming up.
    public record Stats(Double protein, Double micro, Double energy, Double gut, Double phyto) {}


    public static FoodListItem of(Food food, List<Badge> badges, NutritionScore score) {
        return new FoodListItem(food.getId(), food.getName(), food.getCategory(),
                food.getDescription(), food.getServingSizeG(), food.getFoodRole().name(), food.getFrequency(), badges,
                score == null ? null : score.getOverallScore(),
                score == null ? null : new Stats(score.getProteinQuality(), score.getMicronutrientDensity(),
                        score.getEnergyProfile(), score.getGutHealth(), score.getPhytonutrients()));
    }
}
