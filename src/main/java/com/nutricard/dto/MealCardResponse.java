package com.nutricard.dto;

import com.nutricard.model.Meal;
import com.nutricard.model.MealFood;
import com.nutricard.model.MealScore;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MealCardResponse {
    private Meal meal;
    private MealScore mealScore;
    private List<MealFood> foods;
}
