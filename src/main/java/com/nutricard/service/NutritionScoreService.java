package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The one place that turns "a food" into "its score": the saved score if there is one, otherwise
// compute it, save it and return it. Before this, five separate spots each did that by hand.
@Service
@RequiredArgsConstructor
public class NutritionScoreService {

    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;

    public NutritionScore getOrCompute(Food food) {
        return nutritionScoreRepository.findByFoodId(food.getId())
                .orElseGet(() -> compute(food));
    }

    // For a whole meal: one query for the saved scores, then compute only the missing ones.
    public Map<Long, NutritionScore> getOrComputeAll(Collection<Food> foods) {
        List<Long> foodIds = foods.stream().map(Food::getId).distinct().toList();
        Map<Long, NutritionScore> byFoodId = nutritionScoreRepository.findByFoodIdIn(foodIds).stream()
                .collect(Collectors.toMap(s -> s.getFood().getId(), s -> s));
        for (Food food : foods) {
            byFoodId.computeIfAbsent(food.getId(), id -> compute(food));
        }
        return byFoodId;
    }

    // For startup seeding and warm-up: make sure the food has a saved score.
    // Returns true if a new score was saved.
    public boolean computeIfMissing(Food food) {
        if (nutritionScoreRepository.findByFoodId(food.getId()).isPresent()) return false;
        compute(food);
        return true;
    }

    private NutritionScore compute(Food food) {
        return nutritionScoreRepository.save(scoringService.calculateScores(food));
    }
}
