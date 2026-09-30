package com.nutricard.controller;

import com.nutricard.dto.CreateMealRequest;
import com.nutricard.dto.MealCardResponse;
import com.nutricard.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Same idea as FoodController: just routes to MealService, no logic here.
@RestController
@RequestMapping("/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    // @Valid rejects an empty meal or 0 grams before it reaches the service.
    // ApiExceptionHandler turns the failure into a 400 that names the bad field.
    @PostMapping
    public ResponseEntity<MealCardResponse> createMeal(@Valid @RequestBody CreateMealRequest request) {
        return ResponseEntity.ok(mealService.createMeal(request));
    }

    @GetMapping("/{id}/card")
    public MealCardResponse getMealCard(@PathVariable Long id) {
        return mealService.getMealCard(id);
    }
}
