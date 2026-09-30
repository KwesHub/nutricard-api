package com.nutricard.service;

import com.nutricard.dto.MealCardResponse.NutrientAnalysis;
import com.nutricard.model.Food;
import com.nutricard.model.Meal;
import com.nutricard.model.MealFood;
import com.nutricard.model.MealScore;
import com.nutricard.model.NutritionScore;
import com.nutricard.model.TimingContext;
import com.nutricard.repository.MealFoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealScoringServiceTest {

    private final MealFoodRepository mealFoods = mock(MealFoodRepository.class);
    private final NutritionScoreRepository scores = mock(NutritionScoreRepository.class);
    // spy so a test can replace calculateScores while the JSON parsing stays real
    private final ScoringService scoring = spy(new ScoringService(mock(NutrientDataService.class)));
    private final NutritionScoreService scoreService = new NutritionScoreService(scores, scoring, new NutritionScoreWriter(scores));
    private final MealScoringService service = new MealScoringService(mealFoods, scores, scoring, scoreService);

    private Food food(long id, String name) {
        Food f = new Food();
        f.setId(id);
        f.setName(name);
        return f;
    }

    private MealFood mealFood(Food food, int grams) {
        MealFood mf = new MealFood();
        mf.setFood(food);
        mf.setQuantityG(grams);
        return mf;
    }

    private Meal meal(TimingContext timing) {
        Meal m = new Meal();
        m.setId(1L);
        m.setTimingContext(timing);
        return m;
    }

    private NutritionScore score(Food food, double protein, double gut, String timingJson, String coveragesJson) {
        NutritionScore s = new NutritionScore();
        s.setFood(food);
        s.setProteinQuality(protein);
        s.setMicronutrientDensity(0.0);
        s.setEnergyProfile(0.0);
        s.setGutHealth(gut);
        s.setPhytonutrients(0.0);
        s.setTimingScores(timingJson);
        s.setMicroBreakdown("{\"topNutrients\":[],\"coverages\":" + coveragesJson + "}");
        return s;
    }

    private NutritionScore simple(Food food, double protein) {
        return score(food, protein, 0.0, "{}", "{}");
    }

    // Scores the fake repository "has saved", returned by the single batch query
    private final Map<Long, NutritionScore> saved = new HashMap<>();

    private void stubScores(NutritionScore... byFood) {
        for (NutritionScore s : byFood) saved.put(s.getFood().getId(), s);
        // doAnswer, not when(): re-stubbing with when() would call the previous answer with null
        doAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return saved.entrySet().stream()
                    .filter(e -> ids.contains(e.getKey())).map(Map.Entry::getValue).toList();
        }).when(scores).findByFoodIdIn(any());
    }

    private MealScore scoreMeal(TimingContext timing, MealFood... items) {
        Meal m = meal(timing);
        when(mealFoods.findByMealId(1L)).thenReturn(List.of(items));
        return service.calculateMealScore(m);
    }

    // ---- meal scoring ----

    @Test
    void statsAreAveragedByGrams() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        stubScores(simple(a, 80), simple(b, 40));

        MealScore result = scoreMeal(TimingContext.NEUTRAL, mealFood(a, 100), mealFood(b, 300));

        // 100g of 80 and 300g of 40: 0.25 * 80 + 0.75 * 40
        assertEquals(50.0, result.getProteinQuality(), 0.001);
    }

    @Test
    void overallAveragesEachFoodsTimingScoreForTheMealContext() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        stubScores(
                score(a, 0, 0, "{\"PRE_WORKOUT\":60,\"MORNING\":10}", "{}"),
                score(b, 0, 0, "{\"PRE_WORKOUT\":20,\"MORNING\":90}", "{}"));

        MealScore result = scoreMeal(TimingContext.PRE_WORKOUT, mealFood(a, 100), mealFood(b, 100));

        assertEquals(40.0, result.getOverallScore(), 0.001);
    }

    @Test
    void overallFallsBackToWeightedStatsWhenAFoodHasNoTimingScores() {
        Food a = food(1, "A");
        stubScores(simple(a, 100));

        MealScore result = scoreMeal(TimingContext.NEUTRAL, mealFood(a, 100));

        // only protein is non-zero and NEUTRAL weights protein at 0.25
        assertEquals(25.0, result.getOverallScore(), 0.001);
    }

    @Test
    void aFoodWithNoSavedScoreIsComputedInsteadOfDilutingTheMeal() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        stubScores(simple(a, 80));
        NutritionScore computedB = simple(b, 40);
        doReturn(Optional.of(computedB)).when(scoring).calculateFromUsda(b);
        when(scores.save(any(NutritionScore.class))).thenAnswer(inv -> inv.getArgument(0));

        MealScore result = scoreMeal(TimingContext.NEUTRAL, mealFood(a, 100), mealFood(b, 100));

        // the old bug skipped B but still divided by both foods' weight, giving 40 instead of 60
        assertEquals(60.0, result.getProteinQuality(), 0.001);
        verify(scores).save(computedB);
    }

    @Test
    void allOfAMealsScoresComeFromOneQuery() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        Food c = food(3, "C");
        stubScores(simple(a, 10), simple(b, 20), simple(c, 30));

        scoreMeal(TimingContext.NEUTRAL, mealFood(a, 100), mealFood(b, 100), mealFood(c, 100));

        verify(scores, times(1)).findByFoodIdIn(any());
        verify(scores, never()).findByFoodId(any());
    }

    @Test
    void aFoodWithNoScoreAndNoUsdaDataFailsTheMealInsteadOfScoringItAsZero() {
        Food a = food(1, "A");
        Food b = food(2, "Mystery food");
        stubScores(simple(a, 80));
        when(mealFoods.findByMealId(1L)).thenReturn(List.of(mealFood(a, 100), mealFood(b, 100)));

        // the USDA client mock returns nothing for B, and B has no built-in fallback
        assertThrows(ResponseStatusException.class,
                () -> service.calculateMealScore(meal(TimingContext.NEUTRAL)));
        verify(scores, never()).save(any());
    }

    // ---- synergy rules ----

    private String synergies(Food... foods) {
        MealFood[] items = new MealFood[foods.length];
        for (int i = 0; i < foods.length; i++) {
            stubScores(simple(foods[i], 0));
            items[i] = mealFood(foods[i], 100);
        }
        return scoreMeal(TimingContext.NEUTRAL, items).getActiveSynergies();
    }

    @Test
    void fishWithGarlicIsAnOmega3AllicinSynergy() {
        assertTrue(synergies(food(1, "Sardines"), food(2, "Garlic")).contains("Omega-3 + Allicin"));
    }

    @Test
    void oatsWithAVitaminCFoodReducesPhyticAcid() {
        assertTrue(synergies(food(1, "Oats"), food(2, "Kiwi")).contains("phytic acid"));
    }

    @Test
    void spinachWithLemonImprovesIronAbsorption() {
        assertTrue(synergies(food(1, "Spinach"), food(2, "Lemon")).contains("iron absorption"));
    }

    @Test
    void tomatoNeedsDietaryFatForTheLycopeneSynergy() {
        assertTrue(synergies(food(1, "Tomato"), food(2, "Olive oil")).contains("Lycopene"));
        assertFalse(synergies(food(3, "Tomato"), food(4, "Eggs")).contains("Lycopene"));
    }

    @Test
    void unrelatedFoodsHaveNoSynergies() {
        assertEquals("", synergies(food(1, "Eggs"), food(2, "White rice")));
    }

    @Test
    void proteinPlusFibreNeedsOneHighProteinAndOneHighGutFood() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        stubScores(score(a, 70, 0, "{}", "{}"), score(b, 0, 40, "{}", "{}"));

        String result = scoreMeal(TimingContext.NEUTRAL, mealFood(a, 100), mealFood(b, 100)).getActiveSynergies();

        assertTrue(result.contains("Protein + Fibre"));
    }

    // ---- nutrient gaps ----

    private NutrientAnalysis analyse(int grams, String coveragesJson) {
        Food a = food(1, "A");
        stubScores(score(a, 0, 0, "{}", coveragesJson));
        when(scores.findAll()).thenReturn(List.of());
        return service.analyzeNutrients(List.of(mealFood(a, grams)));
    }

    @Test
    void gapThresholdIsBelowTenPercentAndScalesWithGrams() {
        // at 200g every value doubles: iron 10.0 (exactly on the line, not a gap), vitaminC 9.8 (a gap)
        NutrientAnalysis result = analyse(200,
                "{\"iron\":5.0,\"vitaminC\":4.9,\"calcium\":50.0,\"biotin\":0.0}");

        assertEquals(List.of("vitaminC"), result.gaps().stream().map(NutrientAnalysis.Gap::name).toList());
    }

    @Test
    void biotinAndIodineAreNeverReportedAsGaps() {
        NutrientAnalysis result = analyse(100, "{\"biotin\":0.0,\"iodine\":0.0}");

        assertTrue(result.gaps().isEmpty());
    }

    @Test
    void gapsCarryRareFlagAndCadenceAndRareOnesComeFirst() {
        NutrientAnalysis result = analyse(100, "{\"vitaminC\":1.0,\"iron\":1.0}");

        NutrientAnalysis.Gap first = result.gaps().get(0);
        NutrientAnalysis.Gap second = result.gaps().get(1);
        assertEquals("iron", first.name());
        assertTrue(first.rare());
        assertEquals("WEEKLY", first.cadence());
        assertEquals("vitaminC", second.name());
        assertFalse(second.rare());
        assertEquals("DAILY", second.cadence());
    }

    @Test
    void suggestionsRankByGapsCoveredAndSkipFoodsAlreadyInTheMeal() {
        Food inMeal = food(1, "In meal");
        Food both = food(2, "Covers both");
        Food one = food(3, "Covers one");
        NutritionScore mealScore = score(inMeal, 0, 0, "{}", "{\"iron\":1.0,\"zinc\":1.0}");
        stubScores(mealScore);
        when(scores.findAll()).thenReturn(List.of(
                mealScore, // covers nothing useful, and must be excluded anyway
                score(one, 0, 0, "{}", "{\"iron\":40.0,\"zinc\":2.0}"),
                score(both, 0, 0, "{}", "{\"iron\":30.0,\"zinc\":30.0}")));

        NutrientAnalysis result = service.analyzeNutrients(List.of(mealFood(inMeal, 100)));

        assertEquals(List.of("Covers both", "Covers one"),
                result.suggestions().stream().map(NutrientAnalysis.Suggestion::foodName).toList());
        assertEquals(Map.of("iron", 1.0, "zinc", 1.0), Map.of(
                "iron", result.coverage().get("iron"), "zinc", result.coverage().get("zinc")));
    }
}
