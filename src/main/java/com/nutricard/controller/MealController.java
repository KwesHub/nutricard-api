package com.nutricard.controller;

import com.nutricard.dto.CreateMealRequest;
import com.nutricard.dto.MealCardResponse;
import com.nutricard.dto.MealFoodRequest;
import com.nutricard.model.*;
import com.nutricard.repository.*;
import com.nutricard.service.MealScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealRepository mealRepository;
    private final MealFoodRepository mealFoodRepository;
    private final MealScoreRepository mealScoreRepository;
    private final FoodRepository foodRepository;
    private final MealScoringService mealScoringService;

    @PostMapping
    public ResponseEntity<MealCardResponse> createMeal(@RequestBody CreateMealRequest request) {
        Meal meal = new Meal();
        meal.setName(request.getName());
        meal.setTimingContext(request.getTimingContext());
        meal = mealRepository.save(meal);

        for (MealFoodRequest foodReq : request.getFoods()) {
            Food food = foodRepository.findById(foodReq.getFoodId()).orElse(null);
            if (food == null) {
                return ResponseEntity.badRequest().build();
            }
            MealFood mealFood = new MealFood();
            mealFood.setMeal(meal);
            mealFood.setFood(food);
            mealFood.setQuantityG(foodReq.getQuantityG());
            mealFoodRepository.save(mealFood);
        }

        MealScore mealScore = mealScoringService.calculateMealScore(meal);
        mealScoreRepository.save(mealScore);

        List<MealFood> mealFoods = mealFoodRepository.findByMealId(meal.getId());
        return ResponseEntity.ok(new MealCardResponse(meal, mealScore, mealFoods));
    }

    @GetMapping("/{id}/card")
    public ResponseEntity<MealCardResponse> getMealCard(@PathVariable Long id) {
        return mealRepository.findById(id)
                .map(meal -> {
                    MealScore mealScore = mealScoreRepository.findByMealId(meal.getId()).orElse(null);
                    List<MealFood> mealFoods = mealFoodRepository.findByMealId(meal.getId());
                    return ResponseEntity.ok(new MealCardResponse(meal, mealScore, mealFoods));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
