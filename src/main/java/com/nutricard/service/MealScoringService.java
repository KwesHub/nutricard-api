package com.nutricard.service;

import com.nutricard.dto.MealCardResponse.NutrientAnalysis;
import com.nutricard.model.*;
import com.nutricard.repository.MealFoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MealScoringService {

    // A nutrient the whole meal covers below this % of daily RDA is flagged as a gap.
    private static final double GAP_THRESHOLD_PCT = 10.0;
    // A candidate food fills a gap if it covers at least this % RDA per 100g.
    private static final double SUGGESTION_COVER_MIN_PCT = 25.0;
    private static final int SUGGESTION_LIMIT = 3;
    // USDA SR Legacy has almost no data for these — a zero means missing data, not a
    // missing nutrient, so reporting them as gaps would mislead.
    private static final Set<String> GAP_EXCLUDED = Set.of("biotin", "iodine");

    private final MealFoodRepository mealFoodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;

    public MealScore calculateMealScore(Meal meal) {
        List<MealFood> mealFoods = mealFoodRepository.findByMealId(meal.getId());

        double totalWeight = mealFoods.stream().mapToInt(MealFood::getQuantityG).sum();

        double weightedProtein = 0;
        double weightedMicro = 0;
        double weightedEnergy = 0;
        double weightedGut = 0;
        double weightedPhyto = 0;

        List<FoodWithScore> foodsWithScores = new ArrayList<>();

        for (MealFood mealFood : mealFoods) {
            NutritionScore ns = nutritionScoreRepository.findByFoodId(mealFood.getFood().getId())
                    .orElseGet(() -> {
                        NutritionScore computed = scoringService.calculateScores(mealFood.getFood());
                        return nutritionScoreRepository.save(computed);
                    });

            double weight = mealFood.getQuantityG() / totalWeight;
            weightedProtein += ns.getProteinQuality() * weight;
            weightedMicro += ns.getMicronutrientDensity() * weight;
            weightedEnergy += ns.getEnergyProfile() * weight;
            weightedGut += ns.getGutHealth() * weight;
            weightedPhyto += ns.getPhytonutrients() * weight;

            foodsWithScores.add(new FoodWithScore(mealFood.getFood(), ns));
        }

        List<String> synergies = detectSynergies(mealFoods, foodsWithScores);

        double overallScore = calculateOverallByTiming(meal.getTimingContext(),
                weightedProtein, weightedMicro, weightedEnergy, weightedGut, weightedPhyto);

        MealScore mealScore = new MealScore();
        mealScore.setMeal(meal);
        mealScore.setProteinQuality(round(weightedProtein));
        mealScore.setMicronutrientDensity(round(weightedMicro));
        mealScore.setEnergyProfile(round(weightedEnergy));
        mealScore.setGutHealth(round(weightedGut));
        mealScore.setPhytonutrients(round(weightedPhyto));
        mealScore.setBioavailabilityModifier(1.0);
        mealScore.setOverallScore(round(overallScore));
        mealScore.setActiveSynergies(String.join(",", synergies));

        return mealScore;
    }

    // --- Nutrient gap analysis ---
    // Computed at serve time, not persisted: gaps depend on the current coverage data and
    // suggestions depend on what else is in the food database right now.

    public NutrientAnalysis analyzeNutrients(List<MealFood> mealFoods) {
        // Aggregate: each food's %RDA-per-100g coverages, scaled by its gram quantity
        Map<String, Double> coverage = new LinkedHashMap<>();
        for (MealFood mealFood : mealFoods) {
            NutritionScore ns = nutritionScoreRepository.findByFoodId(mealFood.getFood().getId())
                    .orElseGet(() -> nutritionScoreRepository.save(
                            scoringService.calculateScores(mealFood.getFood())));
            double gramFactor = mealFood.getQuantityG() / 100.0;
            scoringService.parseCoverages(ns).forEach((nutrient, pct) ->
                    coverage.merge(nutrient, pct * gramFactor, Double::sum));
        }
        coverage.replaceAll((n, pct) -> Math.round(pct * 10.0) / 10.0);

        List<NutrientAnalysis.Gap> gaps = coverage.entrySet().stream()
                .filter(e -> e.getValue() < GAP_THRESHOLD_PCT && !GAP_EXCLUDED.contains(e.getKey()))
                .map(e -> new NutrientAnalysis.Gap(e.getKey(), ScoringService.isRareNutrient(e.getKey())))
                .sorted(Comparator.comparing(g -> !g.rare()))
                .toList();

        return new NutrientAnalysis(coverage, gaps, suggestFillers(mealFoods, gaps));
    }

    private List<NutrientAnalysis.Suggestion> suggestFillers(List<MealFood> mealFoods,
                                                             List<NutrientAnalysis.Gap> gaps) {
        if (gaps.isEmpty()) return List.of();
        Set<Long> inMeal = mealFoods.stream()
                .map(mf -> mf.getFood().getId())
                .collect(java.util.stream.Collectors.toSet());

        // Only foods with a persisted score are candidates — computing missing ones here
        // would mean USDA API calls inside a request. The startup warm-up closes that gap.
        record Candidate(Food food, List<String> covers, long rareCovers) {}
        return nutritionScoreRepository.findAll().stream()
                .filter(ns -> ns.getFood() != null && !inMeal.contains(ns.getFood().getId()))
                .map(ns -> {
                    Map<String, Double> c = scoringService.parseCoverages(ns);
                    List<String> covers = gaps.stream()
                            .map(NutrientAnalysis.Gap::name)
                            .filter(n -> c.getOrDefault(n, 0.0) >= SUGGESTION_COVER_MIN_PCT)
                            .toList();
                    long rareCovers = covers.stream().filter(ScoringService::isRareNutrient).count();
                    return new Candidate(ns.getFood(), covers, rareCovers);
                })
                .filter(cand -> !cand.covers().isEmpty())
                .sorted(Comparator.comparingInt((Candidate cand) -> cand.covers().size()).reversed()
                        .thenComparing(Comparator.comparingLong(Candidate::rareCovers).reversed()))
                .limit(SUGGESTION_LIMIT)
                .map(cand -> new NutrientAnalysis.Suggestion(
                        cand.food().getId(), cand.food().getName(), cand.covers()))
                .toList();
    }

    private double calculateOverallByTiming(TimingContext timing,
                                            double protein, double micro,
                                            double energy, double gut, double phyto) {
        return switch (timing) {
            case PRE_WORKOUT ->
                    energy * 0.45 + protein * 0.25 + micro * 0.10 + gut * 0.10 + phyto * 0.10;
            case POST_WORKOUT ->
                    protein * 0.40 + micro * 0.20 + energy * 0.15 + gut * 0.20 + phyto * 0.05;
            case MORNING ->
                    protein * 0.25 + micro * 0.25 + energy * 0.25 + gut * 0.15 + phyto * 0.10;
            case EVENING ->
                    gut * 0.35 + micro * 0.25 + phyto * 0.15 + protein * 0.15 + energy * 0.10;
            default ->
                    protein * 0.25 + micro * 0.20 + energy * 0.30 + gut * 0.15 + phyto * 0.10;
        };
}

    private List<String> detectSynergies(List<MealFood> mealFoods, List<FoodWithScore> foodsWithScores) {
        List<String> synergies = new ArrayList<>();
        List<String> foodNames = mealFoods.stream()
                .map(mf -> mf.getFood().getName())
                .toList();

        boolean hasOmega3Fish = foodNames.stream().anyMatch(n ->
                n.equals("Sardines") || n.equals("Mackerel") || n.equals("Salmon"));
        boolean hasAllium = foodNames.stream().anyMatch(n -> n.equals("Garlic") || n.equals("Onion"));
        boolean hasOats = foodNames.contains("Oats");
        boolean hasVitC = foodNames.stream().anyMatch(n ->
                n.equals("Kiwi") || n.equals("Lemon") || n.equals("Orange") ||
                n.equals("Bell pepper") || n.equals("Broccoli") || n.equals("Tomato"));
        boolean hasSpinach = foodNames.contains("Spinach");
        boolean hasVitCForIron = foodNames.stream().anyMatch(n ->
                n.equals("Lemon") || n.equals("Kiwi") || n.equals("Bell pepper") || n.equals("Broccoli"));
        boolean hasTomato = foodNames.contains("Tomato");
        boolean hasDietaryFat = foodNames.stream().anyMatch(n ->
                n.equals("Olive oil") || n.equals("Avocado") || n.equals("Salmon") ||
                n.equals("Sardines") || n.equals("Walnuts") || n.equals("Flaxseed") || n.equals("Chia seeds"));

        if (hasOmega3Fish && hasAllium) {
            synergies.add("Omega-3 + Allicin: anti-inflammatory combination");
        }

        if (hasOats && hasVitC) {
            synergies.add("Vitamin C reduces phytic acid effect in oats — improved mineral absorption");
        }

        if (hasSpinach && hasVitCForIron) {
            synergies.add("Vitamin C improves iron absorption from spinach");
        }

        if (hasTomato && hasDietaryFat) {
            synergies.add("Fat + Lycopene: dietary fat increases lycopene absorption from tomato by up to 4x");
        }

        boolean hasHighProtein = foodsWithScores.stream()
                .anyMatch(fs -> fs.score().getProteinQuality() > 60);
        boolean hasHighGut = foodsWithScores.stream()
                .anyMatch(fs -> fs.score().getGutHealth() > 35);
        if (hasHighProtein && hasHighGut) {
            synergies.add("Protein + Fibre: sustained energy and satiety");
        }

        return synergies;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record FoodWithScore(Food food, NutritionScore score) {}
}
