package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.repository.FoodRepository;
import com.nutricard.seeder.DataSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The scoring maps are keyed on exact food-name strings, so a typo fails silently (the food just
// gets a default). These tests catch that: every map key must be a food that DataSeeder really seeds.
class FoodMapsConsistencyTest {

    private static Set<String> seeded;

    // Run the real DataSeeder against mocks and record which foods it creates
    @BeforeAll
    static void collectSeededFoodNames() {
        FoodRepository foods = mock(FoodRepository.class);
        when(foods.count()).thenReturn(0L);
        when(foods.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));
        when(foods.findAll()).thenReturn(List.of());
        NutritionScoreService scoreService = mock(NutritionScoreService.class);

        new DataSeeder(foods, scoreService, mock(JdbcTemplate.class)).run();

        ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
        verify(scoreService, atLeastOnce()).computeIfMissing(captor.capture());
        seeded = new HashSet<>();
        captor.getAllValues().forEach(f -> seeded.add(f.getName()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> map(Class<?> owner, String field) throws Exception {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        return (Map<String, ?>) f.get(null);
    }

    private static final String[] SCORING_MAPS = {
            "PDCAAS_MAP", "COMPLETENESS_MAP", "BIOAVAILABILITY_MAP", "GI_MAP", "PREBIOTIC_MAP",
            "ANTI_NUTRIENT_MAP", "PHYTO_MAP", "PLANT_COMPOUNDS", "STANDOUT_FACTS", "ANTI_NUTRIENT_NOTES",
            "ANTI_NUTRIENT_BADGES", "CAP_BADGES", "CAP_NOTES"
    };

    @Test
    void seederCreatesTheExpectedNumberOfFoods() {
        assertEquals(41, seeded.size());
    }

    @Test
    void everySeededFoodHasAUsdaId() throws Exception {
        Map<String, ?> fdcIds = map(NutrientDataService.class, "FDC_ID_MAP");
        Set<String> missing = new HashSet<>(seeded);
        missing.removeAll(fdcIds.keySet());
        assertTrue(missing.isEmpty(), "seeded foods with no FDC id: " + missing);
    }

    @Test
    void everyUsdaIdBelongsToASeededFood() throws Exception {
        Set<String> extra = new HashSet<>(map(NutrientDataService.class, "FDC_ID_MAP").keySet());
        extra.removeAll(seeded);
        assertTrue(extra.isEmpty(), "USDA map keys that match no seeded food (typo?): " + extra);
    }

    @Test
    void everyScoringMapKeyIsASeededFood() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String name : SCORING_MAPS) {
            for (String key : map(ScoringService.class, name).keySet()) {
                if (!seeded.contains(key)) problems.add(name + " -> " + key);
            }
        }
        assertTrue(problems.isEmpty(), "map keys that match no seeded food (typo?): " + problems);
    }

    @Test
    void everyAntiNutrientPenaltyHasANoteAndBadge() throws Exception {
        Set<String> penalties = map(ScoringService.class, "ANTI_NUTRIENT_MAP").keySet();
        assertEquals(penalties, map(ScoringService.class, "ANTI_NUTRIENT_NOTES").keySet());
        assertEquals(penalties, map(ScoringService.class, "ANTI_NUTRIENT_BADGES").keySet());
    }

    @Test
    void everyCapBadgeHasANote() throws Exception {
        assertEquals(map(ScoringService.class, "CAP_BADGES").keySet(),
                map(ScoringService.class, "CAP_NOTES").keySet());
        assertEquals(map(ScoringService.class, "INFO_BADGES").keySet(),
                map(ScoringService.class, "INFO_NOTES").keySet());
    }

    @Test
    void everySeededFoodHasTheMapsThatHaveNoSensibleDefault() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String name : new String[]{"GI_MAP", "PHYTO_MAP", "BIOAVAILABILITY_MAP"}) {
            Set<String> missing = new HashSet<>(seeded);
            missing.removeAll(map(ScoringService.class, name).keySet());
            if (!missing.isEmpty()) problems.add(name + " is missing " + missing);
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void everySeededFoodHasVersatilityTraitsAndEveryPairingNamesARealFood() {
        Set<String> noTraits = new HashSet<>(seeded);
        noTraits.removeAll(FoodVersatility.TRAITS.keySet());
        assertTrue(noTraits.isEmpty(), "seeded foods with no versatility traits: " + noTraits);
        Set<String> extraTraits = new HashSet<>(FoodVersatility.TRAITS.keySet());
        extraTraits.removeAll(seeded);
        assertTrue(extraTraits.isEmpty(), "versatility traits for unknown foods: " + extraTraits);

        List<String> unknown = new ArrayList<>();
        for (FoodPairings.Pairing p : FoodPairings.PAIRINGS) {
            p.left().stream().filter(n -> !seeded.contains(n)).forEach(unknown::add);
            p.right().stream().filter(n -> !seeded.contains(n)).forEach(unknown::add);
        }
        assertTrue(unknown.isEmpty(), "pairings naming unknown foods: " + unknown);
    }
}
