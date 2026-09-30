package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.service.NutrientDataService.NutrientData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringServiceTest {

    // Mock the USDA client so no test touches the network
    private final NutrientDataService usda = mock(NutrientDataService.class);
    private final ScoringService service = new ScoringService(usda);

    private Food food(String name) {
        Food f = new Food();
        f.setName(name);
        return f;
    }

    // m is applied to all 24 vitamins/minerals so tests can turn micronutrients on or off
    private NutrientData data(double protein, double fibre, double kcal, double fat,
                              double sugars, double m, double epa, double dha) {
        return new NutrientData(protein, fibre, kcal, fat, 0, sugars, 0, 0,
                m, m, m, m, m, m, m, m, m, m, m, m,
                m, m, m, m, m, m, m, m, m, m, m, m,
                epa, dha);
    }

    private NutritionScore score(String name, NutrientData d) {
        when(usda.fetchNutrientData(anyString())).thenReturn(d);
        return service.calculateScores(food(name));
    }

    @Test
    void overallUsesTopFourStatsAndDropsTheLowest() {
        NutritionScore s = score("Mystery food", data(20, 5, 150, 5, 2, 0, 0, 0));

        double[] stats = {s.getProteinQuality(), s.getMicronutrientDensity(),
                s.getEnergyProfile(), s.getGutHealth(), s.getPhytonutrients()};
        Arrays.sort(stats);
        // stats is ascending, so index 4 is the best stat and index 0 is dropped
        double expected = stats[4] * 0.50 + stats[3] * 0.30 + stats[2] * 0.15 + stats[1] * 0.05;

        assertEquals(expected, s.getOverallScore(), 0.011);
    }

    @Test
    void unknownFoodFallsBackToDefaults() {
        NutritionScore s = score("Mystery food", data(22, 0, 100, 0, 0, 0, 0, 0));

        // 22g protein gives the full 50 volume points, then 0.70 * 0.70 * 50 quality points
        assertEquals(74.5, s.getProteinQuality(), 0.01);
        assertEquals(0.80, s.getBioavailabilityModifier(), 0.001);
        assertEquals(20.0, s.getPhytonutrients(), 0.001);
        assertEquals(40.0, s.getSynergyPotential(), 0.001);
    }

    @Test
    void micronutrientScoreIsCappedAt100() {
        NutritionScore s = score("Mystery food", data(0, 0, 100, 0, 0, 1000, 0, 0));

        assertEquals(100.0, s.getMicronutrientDensity(), 0.001);
    }

    @Test
    void zeroCaloriesDoesNotBreakTheMicronutrientScore() {
        NutritionScore s = score("Mystery food", data(0, 0, 0, 0, 0, 10, 0, 0));

        assertFalse(Double.isNaN(s.getMicronutrientDensity()));
        assertTrue(s.getMicronutrientDensity() >= 0 && s.getMicronutrientDensity() <= 100);
    }

    @Test
    void omega3GutBonusIsCappedAt20() {
        // no fibre, no prebiotic, no penalty, so gut health is only the omega-3 bonus
        NutritionScore s = score("Mystery food", data(0, 0, 100, 0, 0, 0, 5, 5));

        assertEquals(20.0, s.getGutHealth(), 0.001);
    }

    @Test
    void preWorkoutPrefersLightFastFoodOverHeavySlowFood() {
        // same name so same GI, only the fat/fibre/protein "brakes" differ
        NutritionScore light = score("White rice", data(2.7, 0.4, 130, 0.3, 0.1, 0, 0, 0));
        NutritionScore heavy = score("White rice", data(20, 8, 400, 20, 0.1, 0, 0, 0));

        double lightPre = service.parseTimingScores(light).get("PRE_WORKOUT");
        double heavyPre = service.parseTimingScores(heavy).get("PRE_WORKOUT");

        assertTrue(lightPre > heavyPre);
    }

    @Test
    void usdaFailureUsesFallbackForKnownFoodsAndZerosForTheRest() {
        when(usda.fetchNutrientData(anyString())).thenReturn(null);

        assertEquals(88.0, service.calculateScores(food("Sardines")).getProteinQuality(), 0.001);
        assertEquals(0.0, service.calculateScores(food("Mystery food")).getProteinQuality(), 0.001);
    }

    @Test
    void calculateFromUsdaIsEmptyWhenUsdaHasNoData() {
        when(usda.fetchNutrientData(anyString())).thenReturn(null);

        assertTrue(service.calculateFromUsda(food("Sardines")).isEmpty());
    }

    @Test
    void onlySardinesOatsAndGarlicHaveAFallback() {
        assertTrue(service.hasFallback(food("Sardines")));
        assertTrue(service.hasFallback(food("Oats")));
        assertTrue(service.hasFallback(food("Garlic")));
        assertFalse(service.hasFallback(food("Eggs")));
    }
}
