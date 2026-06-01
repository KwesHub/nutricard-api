package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.service.NutrientDataService.NutrientData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScoringService {

    private final NutrientDataService nutrientDataService;

    // --- Lookup maps ---

    private static final Map<String, Double> PDCAAS_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.00), Map.entry("Chicken breast", 0.91),
            Map.entry("Beef mince 10%", 0.92), Map.entry("Sardines", 0.90),
            Map.entry("Red lentils", 0.52), Map.entry("Green lentils", 0.52),
            Map.entry("Red kidney beans", 0.68), Map.entry("Oats", 0.57),
            Map.entry("Brown rice", 0.50), Map.entry("White rice", 0.50),
            Map.entry("Pearl barley", 0.55), Map.entry("Whole-wheat spaghetti", 0.55),
            Map.entry("Peanut butter", 0.52), Map.entry("Peas", 0.65),
            Map.entry("Spinach", 0.70), Map.entry("Sweet potato", 0.70)
    );

    private static final Map<String, Double> COMPLETENESS_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.0), Map.entry("Chicken breast", 1.0),
            Map.entry("Beef mince 10%", 1.0), Map.entry("Sardines", 1.0),
            Map.entry("Peanut butter", 0.62), Map.entry("Red lentils", 0.72),
            Map.entry("Green lentils", 0.72), Map.entry("Red kidney beans", 0.68),
            Map.entry("Oats", 0.70), Map.entry("Brown rice", 0.65),
            Map.entry("White rice", 0.65), Map.entry("Pearl barley", 0.68),
            Map.entry("Whole-wheat spaghetti", 0.65), Map.entry("Peas", 0.75),
            Map.entry("Spinach", 0.70), Map.entry("Sweet potato", 0.70)
    );

    private static final Map<String, Double> BIOAVAILABILITY_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.0), Map.entry("Sardines", 0.95),
            Map.entry("Chicken breast", 0.93), Map.entry("Beef mince 10%", 0.90),
            Map.entry("Sweet potato", 0.85), Map.entry("Kiwi", 0.90),
            Map.entry("Apple", 0.90), Map.entry("Banana", 0.90),
            Map.entry("Blueberries", 0.90), Map.entry("Spinach", 0.65),
            Map.entry("Red lentils", 0.75), Map.entry("Green lentils", 0.75),
            Map.entry("Red kidney beans", 0.75), Map.entry("Oats", 0.70),
            Map.entry("Brown rice", 0.70), Map.entry("Pearl barley", 0.70),
            Map.entry("Whole-wheat spaghetti", 0.72), Map.entry("White rice", 0.68),
            Map.entry("Peas", 0.78), Map.entry("Garlic", 0.85),
            Map.entry("Ginger", 0.85), Map.entry("Honey", 0.90),
            Map.entry("Peanut butter", 0.80), Map.entry("Tahini", 0.80),
            Map.entry("Olive oil", 0.85), Map.entry("Dark chocolate 70%", 0.80)
    );

    private static final Map<String, Integer> GI_MAP = Map.ofEntries(
            Map.entry("White rice", 72), Map.entry("Brown rice", 50),
            Map.entry("Oats", 55), Map.entry("Sardines", 0),
            Map.entry("Garlic", 10), Map.entry("Sweet potato", 44),
            Map.entry("Banana", 51), Map.entry("Blueberries", 25),
            Map.entry("Eggs", 0), Map.entry("Chicken breast", 0),
            Map.entry("Beef mince 10%", 0), Map.entry("Apple", 36),
            Map.entry("Kiwi", 50), Map.entry("Honey", 58),
            Map.entry("Peanut butter", 14), Map.entry("Red lentils", 32),
            Map.entry("Green lentils", 32), Map.entry("Red kidney beans", 24),
            Map.entry("Pearl barley", 25), Map.entry("Whole-wheat spaghetti", 37),
            Map.entry("Peas", 51), Map.entry("Spinach", 15),
            Map.entry("Ginger", 15), Map.entry("Dark chocolate 70%", 23),
            Map.entry("Tahini", 35), Map.entry("Olive oil", 0)
    );

    private static final Map<String, Integer> PREBIOTIC_MAP = Map.of(
            "Garlic", 25, "Oats", 20, "Banana", 15,
            "Peas", 12, "Red lentils", 12, "Green lentils", 12,
            "Red kidney beans", 12, "Sweet potato", 8
    );

    private static final Map<String, Integer> ANTI_NUTRIENT_MAP = Map.of(
            "Red kidney beans", 15, "Red lentils", 8, "Green lentils", 8,
            "Oats", 5, "Spinach", 5
    );

    private static final Map<String, Double> PHYTO_MAP = Map.ofEntries(
            Map.entry("Garlic", 92.0), Map.entry("Blueberries", 95.0),
            Map.entry("Ginger", 90.0), Map.entry("Dark chocolate 70%", 88.0),
            Map.entry("Olive oil", 85.0), Map.entry("Spinach", 82.0),
            Map.entry("Sardines", 80.0), Map.entry("Kiwi", 75.0),
            Map.entry("Apple", 72.0), Map.entry("Oats", 68.0),
            Map.entry("Peas", 65.0), Map.entry("Sweet potato", 65.0),
            Map.entry("Green lentils", 62.0), Map.entry("Red lentils", 60.0),
            Map.entry("Red kidney beans", 60.0), Map.entry("Banana", 55.0),
            Map.entry("Peanut butter", 55.0), Map.entry("Tahini", 52.0),
            Map.entry("Honey", 50.0), Map.entry("Eggs", 45.0),
            Map.entry("Pearl barley", 42.0), Map.entry("Whole-wheat spaghetti", 38.0),
            Map.entry("Brown rice", 35.0), Map.entry("Chicken breast", 30.0),
            Map.entry("Beef mince 10%", 25.0), Map.entry("White rice", 20.0)
    );

    private static final Map<String, Double> SYNERGY_MAP = Map.ofEntries(
            Map.entry("Garlic", 95.0), Map.entry("Olive oil", 90.0),
            Map.entry("Spinach", 85.0), Map.entry("Oats", 80.0),
            Map.entry("Ginger", 78.0), Map.entry("Eggs", 75.0),
            Map.entry("Kiwi", 75.0), Map.entry("White rice", 72.0),
            Map.entry("Brown rice", 68.0), Map.entry("Sardines", 70.0),
            Map.entry("Blueberries", 65.0), Map.entry("Lemon", 92.0),
            Map.entry("Chicken breast", 55.0), Map.entry("Red lentils", 62.0),
            Map.entry("Green lentils", 62.0), Map.entry("Red kidney beans", 60.0),
            Map.entry("Sweet potato", 58.0), Map.entry("Dark chocolate 70%", 58.0),
            Map.entry("Honey", 60.0), Map.entry("Banana", 55.0),
            Map.entry("Peanut butter", 55.0), Map.entry("Tahini", 52.0),
            Map.entry("Peas", 58.0), Map.entry("Pearl barley", 50.0),
            Map.entry("Whole-wheat spaghetti", 48.0), Map.entry("Beef mince 10%", 45.0)
    );

    // Category weights: protein, micro, energy, gut, phyto
    private static final Map<String, double[]> CATEGORY_WEIGHTS = Map.of(
            "PROTEIN", new double[]{0.45, 0.25, 0.15, 0.10, 0.05},
            "FISH",    new double[]{0.35, 0.30, 0.15, 0.05, 0.15},
            "GRAIN",   new double[]{0.10, 0.20, 0.40, 0.20, 0.10},
            "LEGUME",  new double[]{0.20, 0.25, 0.20, 0.25, 0.10},
            "VEGETABLE", new double[]{0.05, 0.35, 0.20, 0.25, 0.15},
            "FRUIT",   new double[]{0.05, 0.25, 0.25, 0.20, 0.25},
            "OTHER",   new double[]{0.00, 0.20, 0.10, 0.25, 0.45}
    );
    private static final double[] DEFAULT_WEIGHTS = {0.25, 0.20, 0.25, 0.15, 0.15};

    private static final String[] NUTRIENT_NAMES = {
            "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminK",
            "vitaminB1", "vitaminB2", "vitaminB3", "vitaminB6", "vitaminB12",
            "folate", "calcium", "iron", "magnesium", "phosphorus",
            "potassium", "zinc", "selenium", "copper"
    };
    private static final double[] RDA_VALUES = {
            900, 90, 20, 15, 120,
            1.2, 1.3, 16, 1.7, 2.4,
            400, 1000, 18, 420, 700,
            3500, 11, 55, 0.9
    };

    // --- Public entry point ---

    public NutritionScore calculateScores(Food food) {
        NutrientData data = nutrientDataService.fetchNutrientData(food.getName());
        if (data != null) {
            return calculateFromRealData(food, data);
        }
        return calculateFallback(food);
    }

    // --- Core scoring ---

    private NutritionScore calculateFromRealData(Food food, NutrientData data) {
        NutritionScore score = new NutritionScore();
        score.setFood(food);

        String name = food.getName();
        String category = food.getCategory() != null ? food.getCategory() : "";

        // Bioavailability
        double bioavailability = BIOAVAILABILITY_MAP.getOrDefault(name, 0.80);

        // 1. Protein quality
        double pdcaas = PDCAAS_MAP.getOrDefault(name, 0.70);
        double completeness = COMPLETENESS_MAP.getOrDefault(name, 0.70);
        double rawProtein = Math.min((data.proteins100g() / 35.0) * pdcaas * completeness * 100, 100);
        double proteinQuality = Math.min(rawProtein * bioavailability, 100);

        // 2. Micronutrient density — peak-weighted RDA coverage
        double[] nutrientValues = {
                data.vitaminA(), data.vitaminC(), data.vitaminD(), data.vitaminE(), data.vitaminK(),
                data.vitaminB1(), data.vitaminB2(), data.vitaminB3(), data.vitaminB6(), data.vitaminB12(),
                data.folate(), data.calcium(), data.iron(), data.magnesium(), data.phosphorus(),
                data.potassium(), data.zinc(), data.selenium(), data.copper()
        };
        double[] coverages = new double[19];
        for (int i = 0; i < 19; i++) {
            coverages[i] = Math.min(nutrientValues[i] / RDA_VALUES[i], 1.0);
        }
        // Find top nutrient before sorting
        int topIdx = 0;
        for (int i = 1; i < 19; i++) {
            if (coverages[i] > coverages[topIdx]) topIdx = i;
        }
        String topNutrientName = NUTRIENT_NAMES[topIdx];
        double topNutrientPct = round(coverages[topIdx] * 100);

        double[] sorted = coverages.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length / 2; i++) {
            double tmp = sorted[i];
            sorted[i] = sorted[sorted.length - 1 - i];
            sorted[sorted.length - 1 - i] = tmp;
        }
        double top3Avg = (sorted[0] + sorted[1] + sorted[2]) / 3.0;
        double next4Avg = (sorted[3] + sorted[4] + sorted[5] + sorted[6]) / 4.0;
        double rem12Avg = 0;
        for (int i = 7; i < 19; i++) rem12Avg += sorted[i];
        rem12Avg /= 12.0;
        double micronutrientDensity = Math.min((top3Avg * 0.50 + next4Avg * 0.30 + rem12Avg * 0.20) * 100 * bioavailability, 100);

        // 3. Energy profile (NEUTRAL default)
        double energyProfile = calculateEnergyProfile(data, name, "NEUTRAL");

        // 4. Gut health
        double fibreScoreGut = Math.min(data.fiber100g() / 10.0, 1.0) * 60;
        int prebioticBonus = PREBIOTIC_MAP.getOrDefault(name, 0);
        int antiNutrientPenalty = ANTI_NUTRIENT_MAP.getOrDefault(name, 0);
        double gutHealth = Math.min(Math.max(fibreScoreGut + prebioticBonus - antiNutrientPenalty, 0), 100);

        // 5. Phytonutrients
        double phytonutrients = PHYTO_MAP.getOrDefault(name, 20.0);

        // Overall score — category-weighted
        double[] w = CATEGORY_WEIGHTS.getOrDefault(category, DEFAULT_WEIGHTS);
        double overallScore = proteinQuality * w[0] + micronutrientDensity * w[1]
                + energyProfile * w[2] + gutHealth * w[3] + phytonutrients * w[4];

        // Synergy potential
        double synergyPotential = SYNERGY_MAP.getOrDefault(name, 40.0);

        // Timing scores
        Map<String, Double> timingScores = calculateTimingScores(food, data,
                proteinQuality, micronutrientDensity, gutHealth, phytonutrients);

        // Energy breakdown values (NEUTRAL context)
        int gi = GI_MAP.getOrDefault(name, 50);
        double unsaturatedRatio = data.fat100g() > 0
                ? (data.monounsaturatedFat100g() + data.polyunsaturatedFat100g()) / data.fat100g()
                : 0.5;

        // Set all fields
        score.setProteinQuality(round(proteinQuality));
        score.setMicronutrientDensity(round(micronutrientDensity));
        score.setEnergyProfile(round(energyProfile));
        score.setGutHealth(round(gutHealth));
        score.setPhytonutrients(round(phytonutrients));
        score.setBioavailabilityModifier(bioavailability);
        score.setOverallScore(round(overallScore));
        score.setSynergyPotential(synergyPotential);
        score.setEnergyProfileNeutral(round(energyProfile));

        // Breakdowns as JSON strings
        score.setProteinBreakdown(String.format(
                "{\"rawProteinG\":%.2f,\"pdcaas\":%.2f,\"completenessFactor\":%.2f,\"bioavailability\":%.2f}",
                data.proteins100g(), pdcaas, completeness, bioavailability));
        score.setEnergyBreakdown(String.format(
                "{\"fibreG\":%.2f,\"gi\":%d,\"sugarsG\":%.2f,\"unsaturatedRatio\":%.2f}",
                data.fiber100g(), gi, data.sugars100g(), unsaturatedRatio));
        score.setGutBreakdown(String.format(
                "{\"fibreG\":%.2f,\"prebioticBonus\":%d,\"antiNutrientPenalty\":%d}",
                data.fiber100g(), prebioticBonus, antiNutrientPenalty));
        score.setMicroBreakdown(String.format(
                "{\"topNutrient\":\"%s\",\"coveragePct\":%.2f}",
                topNutrientName, topNutrientPct));

        // Timing scores as JSON string
        StringBuilder tsJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : timingScores.entrySet()) {
            if (!first) tsJson.append(",");
            tsJson.append(String.format("\"%s\":%.2f", e.getKey(), e.getValue()));
            first = false;
        }
        tsJson.append("}");
        score.setTimingScores(tsJson.toString());

        return score;
    }

    // --- Energy profile with timing context ---

    private double calculateEnergyProfile(NutrientData data, String foodName, String timingContext) {
        int gi = GI_MAP.getOrDefault(foodName, 50);
        double sugarPenalty = Math.min(data.sugars100g() / 20.0, 1.0) * 25;
        double totalFat = data.fat100g();
        double unsaturatedRatio = totalFat > 0
                ? (data.monounsaturatedFat100g() + data.polyunsaturatedFat100g()) / totalFat
                : 0.5;
        double fatQuality = unsaturatedRatio * 20;

        double fibreScore;
        double giScore;

        if ("PRE_WORKOUT".equals(timingContext) || "POST_WORKOUT".equals(timingContext)) {
            giScore = (gi / 100.0) * 25;
            fibreScore = Math.max(30 - (data.fiber100g() * 3), 0);
        } else {
            giScore = (1.0 - (gi / 100.0)) * 25;
            fibreScore = Math.min(data.fiber100g() / 10.0, 1.0) * 30;
        }

        return Math.min(Math.max(fibreScore + (25 - sugarPenalty) + giScore + fatQuality, 0), 100);
    }

    // --- Timing scores for all 5 contexts ---

    private Map<String, Double> calculateTimingScores(Food food, NutrientData data,
            double protein, double micro, double gut, double phyto) {

        String name = food.getName();
        Map<String, Double> result = new LinkedHashMap<>();

        String[] contexts = {"MORNING", "PRE_WORKOUT", "POST_WORKOUT", "EVENING", "NEUTRAL"};
        double[][] timingWeights = {
                {0.25, 0.25, 0.25, 0.15, 0.10},  // MORNING
                {0.20, 0.10, 0.45, 0.05, 0.20},  // PRE_WORKOUT
                {0.45, 0.20, 0.15, 0.15, 0.05},  // POST_WORKOUT
                {0.15, 0.25, 0.10, 0.35, 0.15},  // EVENING
                {0.25, 0.20, 0.25, 0.15, 0.15}   // NEUTRAL
        };

        for (int i = 0; i < contexts.length; i++) {
            double energy = calculateEnergyProfile(data, name, contexts[i]);
            double[] w = timingWeights[i];
            double ts = protein * w[0] + micro * w[1] + energy * w[2] + gut * w[3] + phyto * w[4];
            result.put(contexts[i], round(ts));
        }

        return result;
    }

    // --- Utilities ---

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // --- Fallback ---

    NutritionScore calculateFallback(Food food) {
        NutritionScore score = new NutritionScore();
        score.setFood(food);

        switch (food.getName()) {
            case "Sardines" -> {
                score.setProteinQuality(88.0);
                score.setMicronutrientDensity(85.0);
                score.setEnergyProfile(60.0);
                score.setGutHealth(20.0);
                score.setPhytonutrients(82.0);
                score.setBioavailabilityModifier(0.95);
                score.setOverallScore(78.0);
            }
            case "Oats" -> {
                score.setProteinQuality(45.0);
                score.setMicronutrientDensity(52.0);
                score.setEnergyProfile(78.0);
                score.setGutHealth(85.0);
                score.setPhytonutrients(40.0);
                score.setBioavailabilityModifier(0.75);
                score.setOverallScore(68.0);
            }
            case "Garlic" -> {
                score.setProteinQuality(10.0);
                score.setMicronutrientDensity(48.0);
                score.setEnergyProfile(15.0);
                score.setGutHealth(72.0);
                score.setPhytonutrients(88.0);
                score.setBioavailabilityModifier(0.90);
                score.setOverallScore(71.0);
            }
            default -> {
                score.setProteinQuality(0.0);
                score.setMicronutrientDensity(0.0);
                score.setEnergyProfile(0.0);
                score.setGutHealth(0.0);
                score.setPhytonutrients(0.0);
                score.setBioavailabilityModifier(1.0);
                score.setOverallScore(0.0);
            }
        }

        return score;
    }
}
