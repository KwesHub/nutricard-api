package com.nutricard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FoodService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // A nutrient is a "unique strength" of one food when it covers a meaningful share of the
    // RDA per 100g while the other food barely registers.
    private static final double UNIQUE_STRENGTH_MIN_PCT = 25.0;
    private static final double UNIQUE_STRENGTH_OTHER_MAX_PCT = 10.0;
    private static final int UNIQUE_STRENGTH_LIMIT = 5;

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

        return new FoodCardResponse(food, score, new FoodCardResponse.CardInsights(
                scoringService.getStandoutFact(food.getName()),
                scoringService.getPenaltyNote(food.getName())));
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

        Map<String, Double> coveragesA = parseCoverages(scoreA);
        Map<String, Double> coveragesB = parseCoverages(scoreB);
        Map<String, List<CompareResponse.UniqueNutrient>> uniqueStrengths = new LinkedHashMap<>();
        uniqueStrengths.put("foodA", uniqueStrengths(coveragesA, coveragesB));
        uniqueStrengths.put("foodB", uniqueStrengths(coveragesB, coveragesA));

        return new CompareResponse(buildSummary(foodA, scoreA), buildSummary(foodB, scoreB),
                winners, uniqueStrengths);
    }

    private Map<String, Double> parseCoverages(NutritionScore score) {
        if (score.getMicroBreakdown() == null) return Map.of();
        try {
            JsonNode coverages = MAPPER.readTree(score.getMicroBreakdown()).path("coverages");
            Map<String, Double> result = new LinkedHashMap<>();
            coverages.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asDouble()));
            return result;
        } catch (Exception e) {
            log.warn("Could not parse microBreakdown for score {}: {}", score.getId(), e.getMessage());
            return Map.of();
        }
    }

    private List<CompareResponse.UniqueNutrient> uniqueStrengths(Map<String, Double> mine,
                                                                 Map<String, Double> theirs) {
        return mine.entrySet().stream()
                .filter(e -> e.getValue() >= UNIQUE_STRENGTH_MIN_PCT
                        && theirs.getOrDefault(e.getKey(), 0.0) < UNIQUE_STRENGTH_OTHER_MAX_PCT)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(UNIQUE_STRENGTH_LIMIT)
                .map(e -> new CompareResponse.UniqueNutrient(
                        e.getKey(), e.getValue(), ScoringService.isRareNutrient(e.getKey())))
                .toList();
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
