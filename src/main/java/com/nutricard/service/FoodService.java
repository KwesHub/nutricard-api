package com.nutricard.service;

import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.dto.FoodListItem;
import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodService {

    // A nutrient is a "unique strength" of one food when it covers a meaningful share of the
    // RDA per 100g while the other food barely registers.
    private static final double UNIQUE_STRENGTH_MIN_PCT = 25.0;
    private static final double UNIQUE_STRENGTH_OTHER_MAX_PCT = 10.0;
    private static final int UNIQUE_STRENGTH_LIMIT = 5;
    // "Leads on": both foods carry the nutrient (so it isn't unique), but this food covers
    // at least 1.5x as much. Without this, similar foods (oats vs brown rice) read as
    // "brings nothing" even when one clearly wins most shared nutrients.
    private static final double LEADS_ON_RATIO = 1.5;
    private static final int LEADS_ON_LIMIT = 5;

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;
    private final EntityManager entityManager;

    public List<FoodListItem> getAll(String search) {
        List<Food> foods = (search != null && !search.isBlank())
                ? foodRepository.findByNameContainingIgnoreCase(search)
                : foodRepository.findAll();
        // One query for all scores instead of one per food; foods whose score hasn't been
        // computed yet (mid warm-up) simply get no badges.
        Map<Long, NutritionScore> scoresByFoodId = nutritionScoreRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getFood().getId(), s -> s));
        return foods.stream()
                .map(f -> FoodListItem.of(f,
                        scoringService.deriveBadges(scoresByFoodId.get(f.getId()), f.getName())))
                .toList();
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
            // Response-only null: detach first, or JPA dirty checking flushes the null to the
            // DB and permanently wipes timing_scores (which meal timing scoring depends on).
            entityManager.detach(score);
            score.setTimingScores(null);
        }

        return new FoodCardResponse(food, score, new FoodCardResponse.CardInsights(
                scoringService.getStandoutFact(food.getName()),
                scoringService.getPenaltyNote(food.getName()),
                scoringService.deriveBadges(score, food.getName())));
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

        Map<String, Double> coveragesA = scoringService.parseCoverages(scoreA);
        Map<String, Double> coveragesB = scoringService.parseCoverages(scoreB);
        Map<String, List<CompareResponse.UniqueNutrient>> uniqueStrengths = new LinkedHashMap<>();
        uniqueStrengths.put("foodA", uniqueStrengths(coveragesA, coveragesB));
        uniqueStrengths.put("foodB", uniqueStrengths(coveragesB, coveragesA));
        Map<String, List<CompareResponse.LeadingNutrient>> leadsOn = new LinkedHashMap<>();
        leadsOn.put("foodA", leadsOn(coveragesA, coveragesB));
        leadsOn.put("foodB", leadsOn(coveragesB, coveragesA));

        return new CompareResponse(buildSummary(foodA, scoreA), buildSummary(foodB, scoreB),
                winners, uniqueStrengths, leadsOn);
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

    // Nutrients where the other food clears the unique-strength floor (so it's not "unique")
    // but this food still covers at least LEADS_ON_RATIO times more.
    private List<CompareResponse.LeadingNutrient> leadsOn(Map<String, Double> mine,
                                                          Map<String, Double> theirs) {
        return mine.entrySet().stream()
                .filter(e -> e.getValue() >= UNIQUE_STRENGTH_MIN_PCT
                        && theirs.getOrDefault(e.getKey(), 0.0) >= UNIQUE_STRENGTH_OTHER_MAX_PCT
                        && e.getValue() >= theirs.get(e.getKey()) * LEADS_ON_RATIO)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(LEADS_ON_LIMIT)
                .map(e -> new CompareResponse.LeadingNutrient(
                        e.getKey(), e.getValue(), theirs.get(e.getKey()),
                        ScoringService.isRareNutrient(e.getKey())))
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
