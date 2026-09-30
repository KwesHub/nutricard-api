package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// The one place that turns "a food" into "its score": the saved score if there is one, otherwise
// compute it from USDA, save it and return it. A fallback score is never saved, so a USDA outage
// can't leave zeros in the database as if they were real.
@Service
@RequiredArgsConstructor
public class NutritionScoreService {

    private static final Logger log = LoggerFactory.getLogger(NutritionScoreService.class);

    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;
    private final NutritionScoreWriter nutritionScoreWriter;

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

    // For startup seeding and warm-up: save a score if the food has none and USDA can supply one.
    // Returns true if a new score was saved. When USDA can't, nothing is saved and the score is
    // computed on the first request instead.
    public boolean computeIfMissing(Food food) {
        if (nutritionScoreRepository.findByFoodId(food.getId()).isPresent()) return false;
        Optional<NutritionScore> computed = scoringService.calculateFromUsda(food);
        if (computed.isEmpty()) {
            log.warn("No USDA data for '{}', so no score was saved; it will be computed on first request",
                    food.getName());
            return false;
        }
        try {
            nutritionScoreWriter.saveNow(computed.get());
            return true;
        } catch (DataIntegrityViolationException e) {
            if (nutritionScoreRepository.findByFoodId(food.getId()).isPresent()) return false; // someone else saved it first
            throw e;
        }
    }

    // Saved in its own transaction (see NutritionScoreWriter). If another thread saved a score for this
    // food first, the unique food_id constraint rejects ours and we return theirs instead. Any other
    // integrity failure is not a race, so it is rethrown.
    private NutritionScore saveOrTakeExisting(NutritionScore score) {
        try {
            return nutritionScoreWriter.saveNow(score);
        } catch (DataIntegrityViolationException e) {
            return nutritionScoreRepository.findByFoodId(score.getFood().getId()).orElseThrow(() -> e);
        }
    }

    // Real data: save it. No USDA data: serve the built-in fallback if the food has one (not saved, so
    // the next request tries USDA again), otherwise a 503, because saving zeros would look like a real score.
    private NutritionScore compute(Food food) {
        Optional<NutritionScore> computed = scoringService.calculateFromUsda(food);
        if (computed.isPresent()) {
            return saveOrTakeExisting(computed.get());
        }
        if (scoringService.hasFallback(food)) {
            log.warn("No USDA data for '{}', serving its built-in fallback score without saving it", food.getName());
            return scoringService.calculateFallback(food);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Nutrition data for " + food.getName() + " is unavailable right now, please try again later");
    }
}
