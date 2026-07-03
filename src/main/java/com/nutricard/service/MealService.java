package com.nutricard.service;

import com.nutricard.dto.CreateMealRequest;
import com.nutricard.dto.MealCardResponse;
import com.nutricard.dto.MealFoodRequest;
import com.nutricard.model.*;
import com.nutricard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MealService {

    private final MealRepository mealRepository;
    private final MealFoodRepository mealFoodRepository;
    private final MealScoreRepository mealScoreRepository;
    private final FoodRepository foodRepository;
    private final MealScoringService mealScoringService;

    @Transactional
    public MealCardResponse createMeal(CreateMealRequest request) {
        Meal meal = new Meal();
        meal.setName(request.getName());
        meal.setTimingContext(request.getTimingContext());
        meal = mealRepository.save(meal);

        for (MealFoodRequest foodReq : request.getFoods()) {
            Food food = foodRepository.findById(foodReq.getFoodId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Food not found: " + foodReq.getFoodId()));
            MealFood mealFood = new MealFood();
            mealFood.setMeal(meal);
            mealFood.setFood(food);
            mealFood.setQuantityG(foodReq.getQuantityG());
            mealFoodRepository.save(mealFood);
        }

        MealScore mealScore = mealScoringService.calculateMealScore(meal);
        mealScoreRepository.save(mealScore);

        List<MealFood> mealFoods = mealFoodRepository.findByMealId(meal.getId());
        return new MealCardResponse(meal, mealScore, mealFoods);
    }

    public MealCardResponse getMealCard(Long id) {
        Meal meal = mealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        MealScore mealScore = mealScoreRepository.findByMealId(meal.getId()).orElse(null);
        List<MealFood> mealFoods = mealFoodRepository.findByMealId(meal.getId());
        return new MealCardResponse(meal, mealScore, mealFoods);
    }
}
