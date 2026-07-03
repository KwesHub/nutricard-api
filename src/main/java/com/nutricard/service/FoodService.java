package com.nutricard.service;

import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;

    public List<Food> getAll(String search) {
        if (search != null && !search.isBlank()) {
            return foodRepository.findByNameContainingIgnoreCase(search);
        }
        return foodRepository.findAll();
    }

    @Transactional
    public FoodCardResponse getCard(Long id) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));

        NutritionScore score = nutritionScoreRepository.findByFoodId(food.getId())
                .orElseGet(() -> {
                    NutritionScore calculated = scoringService.calculateScores(food);
                    return nutritionScoreRepository.save(calculated);
                });

        if (food.getFoodRole() == FoodRole.PANTRY || food.getFoodRole() == FoodRole.OCCASIONAL) {
            score.setTimingScores(null);
        }

        return new FoodCardResponse(food, score);
    }

    @Transactional
    public CompareResponse compare(Long a, Long b) {
        Food foodA = foodRepository.findById(a)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Food with ID " + a + " not found"));
        Food foodB = foodRepository.findById(b)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Food with ID " + b + " not found"));

        NutritionScore scoreA = nutritionScoreRepository.findByFoodId(a)
                .orElseGet(() -> nutritionScoreRepository.save(scoringService.calculateScores(foodA)));
        NutritionScore scoreB = nutritionScoreRepository.findByFoodId(b)
                .orElseGet(() -> nutritionScoreRepository.save(scoringService.calculateScores(foodB)));

        Map<String, String> winners = new LinkedHashMap<>();
        winners.put("proteinQuality", pickWinner(scoreA.getProteinQuality(), scoreB.getProteinQuality(), foodA.getName(), foodB.getName()));
        winners.put("micronutrientDensity", pickWinner(scoreA.getMicronutrientDensity(), scoreB.getMicronutrientDensity(), foodA.getName(), foodB.getName()));
        winners.put("energyProfile", pickWinner(scoreA.getEnergyProfile(), scoreB.getEnergyProfile(), foodA.getName(), foodB.getName()));
        winners.put("gutHealth", pickWinner(scoreA.getGutHealth(), scoreB.getGutHealth(), foodA.getName(), foodB.getName()));
        winners.put("phytonutrients", pickWinner(scoreA.getPhytonutrients(), scoreB.getPhytonutrients(), foodA.getName(), foodB.getName()));
        winners.put("overall", pickWinner(scoreA.getOverallScore(), scoreB.getOverallScore(), foodA.getName(), foodB.getName()));

        return new CompareResponse(buildSummary(foodA, scoreA), buildSummary(foodB, scoreB), winners);
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
