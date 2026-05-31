package com.nutricard.controller;

import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;

    @GetMapping
    public List<Food> getAllFoods(@RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return foodRepository.findByNameContainingIgnoreCase(search);
        }
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
