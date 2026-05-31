package com.nutricard.controller;

import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;

    @GetMapping
    public List<Food> getAllFoods() {
        return foodRepository.findAll();
    }

    @GetMapping("/{id}/card")
    public ResponseEntity<FoodCardResponse> getFoodCard(@PathVariable Long id) {
        return foodRepository.findById(id)
                .map(food -> {
                    NutritionScore score = nutritionScoreRepository.findByFoodId(food.getId())
                            .orElse(null);
                    return ResponseEntity.ok(new FoodCardResponse(food, score));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
