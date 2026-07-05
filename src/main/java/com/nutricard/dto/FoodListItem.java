package com.nutricard.dto;

import com.nutricard.model.Food;

import java.util.List;

// GET /foods response shape: all Food fields flattened (backwards compatible with the
// previous raw-entity response) plus derived badges.
public record FoodListItem(Long id, String name, String category, String description,
                           Integer servingSizeG, String foodRole, List<Badge> badges) {

    public static FoodListItem of(Food food, List<Badge> badges) {
        return new FoodListItem(food.getId(), food.getName(), food.getCategory(),
                food.getDescription(), food.getServingSizeG(), food.getFoodRole().name(), badges);
    }
}
