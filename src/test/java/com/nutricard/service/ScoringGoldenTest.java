package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.service.NutrientDataService.NutrientData;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Safety net for refactoring ScoringService: feeds fixed fake nutrient data for every food name
// through the scoring code and compares every output field with a saved copy. If a refactor
// changes any score, breakdown or badge, this fails.
// To regenerate after an intended scoring change: mvn test -Dtest=ScoringGoldenTest -Dupdate.golden=true
class ScoringGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/scoring-golden.txt");

    private static final String[] FOODS = {
            "Sardines", "Oats", "Garlic", "Eggs", "Chicken breast", "Beef mince 10%", "Sweet potato",
            "Brown rice", "White rice", "Pearl barley", "Whole-wheat spaghetti", "Red lentils",
            "Green lentils", "Red kidney beans", "Peas", "Spinach", "Apple", "Banana", "Kiwi",
            "Blueberries", "Ginger", "Honey", "Peanut butter", "Tahini", "Olive oil",
            "Dark chocolate 70%", "Salmon", "Greek yogurt", "Broccoli", "Avocado", "Quinoa",
            "Black beans", "Walnuts", "Cottage cheese", "Lemon", "Flaxseed", "Chia seeds",
            "Sweet corn", "Bell pepper", "Tomato", "Brazil nuts", "Mystery food"
    };

    // protein, fibre, kcal, fat, satFat, sugars, mono, poly, then the 26 tracked nutrients
    private static final double[] SCALES = {
            30, 15, 580, 60, 20, 30, 20, 20,
            1350, 135, 30, 22, 180, 1.8, 2, 24, 2.5, 3.6, 600, 1500, 27, 630, 1050,
            5250, 16, 80, 1.3, 800, 7, 45, 3.5, 220, 1, 1
    };

    // Nutrient levels vary per food so some micronutrient scores land well under the 100 cap
    // (a capped score would hide a wrong rarity weight) and others are high enough for badges.
    private static final double[] LEVELS = {0.05, 0.1, 0.2, 0.4, 0.8, 1.5};

    private NutrientData fakeData(String name, int index) {
        Random r = new Random(name.hashCode());
        double[] v = new double[SCALES.length];
        for (int i = 0; i < v.length; i++) {
            double level = i >= 8 ? LEVELS[index % LEVELS.length] : 1.0;
            v[i] = r.nextDouble() * SCALES[i] * level;
        }
        v[2] += 20; // keep kcal above zero
        return new NutrientData(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17],
                v[18], v[19], v[20], v[21], v[22], v[23], v[24], v[25], v[26], v[27],
                v[28], v[29], v[30], v[31], v[32], v[33], 0);
    }

    private String describe(ScoringService service, NutritionScore s, String name) {
        return String.join(" | ", name,
                "protein=" + s.getProteinQuality(), "micro=" + s.getMicronutrientDensity(),
                "energy=" + s.getEnergyProfile(), "gut=" + s.getGutHealth(),
                "phyto=" + s.getPhytonutrients(), "overall=" + s.getOverallScore(),
                "bio=" + s.getBioavailabilityModifier(), "kcal=" + s.getKcalPer100g(),
                "synergy=" + s.getSynergyPotential(), "neutral=" + s.getEnergyProfileNeutral(),
                "timing=" + s.getTimingScores(), "proteinB=" + s.getProteinBreakdown(),
                "energyB=" + s.getEnergyBreakdown(), "gutB=" + s.getGutBreakdown(),
                "microB=" + s.getMicroBreakdown(),
                "badges=" + service.deriveBadges(s, name));
    }

    @Test
    void scoresMatchSavedGoldenFile() throws Exception {
        NutrientDataService usda = mock(NutrientDataService.class);
        ScoringService service = new ScoringService(usda);

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < FOODS.length; i++) {
            String name = FOODS[i];
            when(usda.fetchNutrientData(anyString())).thenReturn(fakeData(name, i));
            Food f = new Food();
            f.setName(name);
            lines.add(describe(service, service.calculateScores(f), name));
        }

        // the USDA-down path too
        NutrientDataService down = mock(NutrientDataService.class);
        ScoringService fallbackService = new ScoringService(down);
        for (String name : new String[]{"Sardines", "Oats", "Garlic", "Eggs"}) {
            Food f = new Food();
            f.setName(name);
            lines.add(describe(fallbackService, fallbackService.calculateScores(f), "fallback:" + name));
        }

        String actual = String.join("\n", lines) + "\n";
        if (Files.notExists(GOLDEN) || Boolean.getBoolean("update.golden")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual);
            return;
        }
        assertEquals(Files.readString(GOLDEN), actual);
    }
}
