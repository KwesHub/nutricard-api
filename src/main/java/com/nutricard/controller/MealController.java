package com.nutricard.controller;

import com.nutricard.dto.CreateMealRequest;
import com.nutricard.dto.MealCardResponse;
import com.nutricard.service.MealService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @PostMapping
    public ResponseEntity<MealCardResponse> createMeal(@RequestBody CreateMealRequest request) {
        return ResponseEntity.ok(mealService.createMeal(request));
    }

    @GetMapping("/{id}/card")
    public MealCardResponse getMealCard(@PathVariable Long id) {
        return mealService.getMealCard(id);
    }
}
