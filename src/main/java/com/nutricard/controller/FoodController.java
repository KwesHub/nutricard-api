package com.nutricard.controller;

import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/compare")
    public ResponseEntity<?> compareFoods(@RequestParam Long a, @RequestParam Long b) {
        Food foodA = foodRepository.findById(a).orElse(null);
        Food foodB = foodRepository.findById(b).orElse(null);

        if (foodA == null && foodB == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Foods with IDs " + a + " and " + b + " not found"));
        }
        if (foodA == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Food with ID " + a + " not found"));
        }
        if (foodB == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Food with ID " + b + " not found"));
        }

        NutritionScore scoreA = nutritionScoreRepository.findByFoodId(a).orElse(null);
        NutritionScore scoreB = nutritionScoreRepository.findByFoodId(b).orElse(null);

        if (scoreA == null || scoreB == null) {
            return ResponseEntity.status(404).body(Map.of("error", "NutritionScore missing for one or both foods"));
        }

        CompareResponse.FoodSummary summaryA = buildSummary(foodA, scoreA);
        CompareResponse.FoodSummary summaryB = buildSummary(foodB, scoreB);

        Map<String, String> winner = new LinkedHashMap<>();
        winner.put("proteinQuality", pickWinner(scoreA.getProteinQuality(), scoreB.getProteinQuality(), foodA.getName(), foodB.getName()));
        winner.put("micronutrientDensity", pickWinner(scoreA.getMicronutrientDensity(), scoreB.getMicronutrientDensity(), foodA.getName(), foodB.getName()));
        winner.put("energyProfile", pickWinner(scoreA.getEnergyProfile(), scoreB.getEnergyProfile(), foodA.getName(), foodB.getName()));
        winner.put("gutHealth", pickWinner(scoreA.getGutHealth(), scoreB.getGutHealth(), foodA.getName(), foodB.getName()));
        winner.put("phytonutrients", pickWinner(scoreA.getPhytonutrients(), scoreB.getPhytonutrients(), foodA.getName(), foodB.getName()));
        winner.put("overall", pickWinner(scoreA.getOverallScore(), scoreB.getOverallScore(), foodA.getName(), foodB.getName()));

        return ResponseEntity.ok(new CompareResponse(summaryA, summaryB, winner));
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

    private CompareResponse.FoodSummary buildSummary(Food food, NutritionScore score) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("proteinQuality", score.getProteinQuality());
        scores.put("micronutrientDensity", score.getMicronutrientDensity());
        scores.put("energyProfile", score.getEnergyProfile());
        scores.put("gutHealth", score.getGutHealth());
        scores.put("phytonutrients", score.getPhytonutrients());
        scores.put("overall", score.getOverallScore());
        return new CompareResponse.FoodSummary(food.getId(), food.getName(), food.getFoodRole().name(), scores);
    }

    private String pickWinner(Double a, Double b, String nameA, String nameB) {
        if (a > b) return nameA;
        if (b > a) return nameB;
        return "tie";
    }
}
