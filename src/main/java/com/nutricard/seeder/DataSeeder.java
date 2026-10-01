package com.nutricard.seeder;

import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.repository.FoodRepository;
import com.nutricard.service.NutritionScoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final FoodRepository foodRepository;
    private final NutritionScoreService nutritionScoreService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        applyMigrations();

        if (foodRepository.count() > 0) {
            seedMissingFoods();
        } else {
            seedFoods();
        }

        warmUpMissingScores();
    }

    // Migrations drop stale scores, which normally recompute lazily per card view. Meal gap
    // suggestions, however, draw only on persisted scores — so recompute the missing ones in
    // the background rather than serving thin suggestions until every card has been viewed.
    private void warmUpMissingScores() {
        Thread warmup = new Thread(() -> {
            int computed = 0;
            for (Food food : foodRepository.findAll()) {
                try {
                    if (nutritionScoreService.computeIfMissing(food)) computed++;
                } catch (Exception e) {
                    log.warn("Score warm-up failed for '{}': {}", food.getName(), e.getMessage());
                }
            }
            if (computed > 0) log.info("Score warm-up computed {} missing scores", computed);
        }, "score-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    // Role on the plate and how much/how often to eat it, one row per food. Every frequency comes
    // from a published rule, not a guess: NHS 5 A Day (fruit, veg, sweet potato; pulses count once),
    // NHS Eatwell (starchy carbs as the base of meals, some dairy daily), NHS fish advice (at least
    // two portions a week, one oily: a minimum), NHS red meat advice (70g a day or less: a ceiling),
    // no NHS limit on eggs, NHS free sugars (honey counts), EFSA selenium upper limit (Brazil nuts),
    // and standard portions for nuts, seeds and oils (about 30g; 1 tbsp). Flax must be milled to be
    // digested and chia soaked (choking risk), per the owner's notes.
    private record Guide(FoodRole role, String frequency) {}

    private static final Map<String, Guide> FOOD_GUIDE = Map.ofEntries(
            Map.entry("Sardines", new Guide(FoodRole.PROTEIN, "At least 2× a week")),
            Map.entry("Oats", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("Garlic", new Guide(FoodRole.FLAVOUR, "Freely, in cooking")),
            Map.entry("Eggs", new Guide(FoodRole.PROTEIN, "No set limit")),
            Map.entry("Chicken breast", new Guide(FoodRole.PROTEIN, "No set limit")),
            Map.entry("Beef mince 10%", new Guide(FoodRole.PROTEIN, "Up to 70g a day")),
            Map.entry("Sweet potato", new Guide(FoodRole.BASE, "Daily, counts as 1 of 5 a day")),
            Map.entry("Brown rice", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("White rice", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("Pearl barley", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("Whole-wheat spaghetti", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("Red lentils", new Guide(FoodRole.BASE, "Daily, counts as 1 of 5 a day")),
            Map.entry("Green lentils", new Guide(FoodRole.BASE, "Daily, counts as 1 of 5 a day")),
            Map.entry("Red kidney beans", new Guide(FoodRole.BASE, "Daily, counts as 1 of 5 a day")),
            Map.entry("Peas", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Spinach", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Apple", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Banana", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Kiwi", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Blueberries", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Ginger", new Guide(FoodRole.FLAVOUR, "Freely, in cooking")),
            Map.entry("Honey", new Guide(FoodRole.TREAT, "Now and then (free sugar)")),
            Map.entry("Peanut butter", new Guide(FoodRole.BOOSTER, "About 1 tbsp")),
            Map.entry("Tahini", new Guide(FoodRole.FLAVOUR, "About 1 tbsp")),
            Map.entry("Olive oil", new Guide(FoodRole.FLAVOUR, "Small amounts, in cooking")),
            Map.entry("Dark chocolate 70%", new Guide(FoodRole.TREAT, "Now and then")),
            Map.entry("Salmon", new Guide(FoodRole.PROTEIN, "At least 2× a week")),
            Map.entry("Greek yogurt", new Guide(FoodRole.PROTEIN, "Daily, as your dairy")),
            Map.entry("Broccoli", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Avocado", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Quinoa", new Guide(FoodRole.BASE, "Daily, as a meal base")),
            Map.entry("Black beans", new Guide(FoodRole.BASE, "Daily, counts as 1 of 5 a day")),
            Map.entry("Walnuts", new Guide(FoodRole.BOOSTER, "About 30g a day")),
            Map.entry("Cottage cheese", new Guide(FoodRole.PROTEIN, "Daily, as your dairy")),
            Map.entry("Lemon", new Guide(FoodRole.FLAVOUR, "Freely, in cooking")),
            Map.entry("Flaxseed", new Guide(FoodRole.BOOSTER, "1–2 tbsp, milled")),
            Map.entry("Chia seeds", new Guide(FoodRole.BOOSTER, "1 tbsp, soaked")),
            Map.entry("Sweet corn", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Bell pepper", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Tomato", new Guide(FoodRole.VEG_FRUIT, "Daily, part of 5 a day")),
            Map.entry("Brazil nuts", new Guide(FoodRole.BOOSTER, "Max 2 a day"))
    );

    private void applyMigrations() {
        // Fix 1: White rice was seeded as PANTRY; correct the role
        jdbcTemplate.update(
                "UPDATE foods SET food_role = 'DAILY_DRIVER' WHERE name = 'White rice' AND food_role = 'PANTRY'");
        // Drop white rice score if timing_scores is null (it was computed while role was PANTRY)
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'White rice') AND timing_scores IS NULL");
        // Fixes 2, 3, 4, 7 and 8 were removed (2026-10-01). They found stale rows by a score VALUE
        // (e.g. "peanut butter micro outside 55-62"), and later scoring changes moved those values,
        // so Fix 7 started deleting every score on every startup. Every row they targeted also
        // lacks the keys checked by Fixes 12, 13 and 15, which still remove it. Rule: a canary
        // must be a structural marker (a JSON key), never a score value.
        // Fix 5: Drop scores for all newly added foods that may have been computed without the
        // omega-3 bonus. Also drops sardines if Fix 4 above was already a no-op (idempotent).
        // Canary: gut_breakdown column without omega3Bonus field identifies pre-bonus scores.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE gut_breakdown NOT LIKE '%omega3Bonus%'");
        // Fix 6: Micronutrient scoring is now rarity-weighted and micro_breakdown carries the
        // full per-nutrient coverage vector. Canary: rows without the topNutrients key predate
        // the change (IS NULL covers fallback-scored rows, which had no breakdown at all).
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE micro_breakdown IS NULL " +
                "OR micro_breakdown NOT LIKE '%topNutrients%'");
        // Fix 9: FoodService.getCard() used to null timingScores on a managed entity for
        // PANTRY/OCCASIONAL foods, and dirty checking flushed the null to the DB — wiping
        // timing data that meal timing scoring needs. Drop wiped rows so they recompute.
        // Fallback-scored rows persist "{}" instead of NULL, so this stays a no-op for them.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE timing_scores IS NULL");
        // Fix 10: role audit against the criteria in FUTURE_PLANS.md. WEEKLY_ANCHOR now means
        // a genuine moderation-bound "2-3x a week" recommendation (oily fish, red meat).
        // Legumes/whole grains/avocado are fine daily; 30g-serving nuts/nut butters are
        // boosters. Scores don't depend on role, so no rescore needed. Idempotent via the
        // AND food_role = 'WEEKLY_ANCHOR' guard.
        jdbcTemplate.update(
                "UPDATE foods SET food_role = 'DAILY_DRIVER' WHERE food_role = 'WEEKLY_ANCHOR' " +
                "AND name IN ('Red lentils','Green lentils','Red kidney beans','Black beans'," +
                "'Quinoa','Pearl barley','Avocado')");
        jdbcTemplate.update(
                "UPDATE foods SET food_role = 'BOOSTER' WHERE food_role = 'WEEKLY_ANCHOR' " +
                "AND name IN ('Walnuts','Peanut butter')");
        // Fix 11: energy/timing scoring moved from a GI-centric formula to a two-axis
        // gastric-emptying model (stomach speed vs blood speed), so every food's energy
        // profile and timing scores change. Rescore all; the stomachSpeed key in
        // energy_breakdown is the canary. Fallback rows carry the key too, so they survive.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE energy_breakdown IS NULL " +
                "OR energy_breakdown NOT LIKE '%stomachSpeed%'");
        // Fix 12: micronutrient density moved from a hard 100-cap (23 of 41 foods at 100) to a
        // saturating curve. The scoreCurve key in micro_breakdown is the canary.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE micro_breakdown IS NULL " +
                "OR micro_breakdown NOT LIKE '%scoreCurve%'");
        // Fix 13: scoring audit (SCORING_AUDIT.md). Seven foods moved to the correct as-eaten USDA
        // entry, micronutrients gained shortfall weights, fibre and a 50 kcal floor, protein quality
        // now scales with protein amount, the overall drops energy. Rescore all; fibre appearing in
        // micro_breakdown's coverages is the canary.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE micro_breakdown IS NULL " +
                "OR micro_breakdown NOT LIKE '%\"fibre\":%'");
        // Fix 14: roles split into role on the plate (BASE, PROTEIN, VEG_FRUIT, BOOSTER, FLAVOUR,
        // TREAT) and a separate frequency. Hibernate created a CHECK constraint listing the old enum
        // values, so drop it first. Sets every food from FOOD_GUIDE by name; idempotent. Scores don't
        // depend on role, so nothing is rescored.
        jdbcTemplate.execute("ALTER TABLE foods DROP CONSTRAINT IF EXISTS foods_food_role_check");
        jdbcTemplate.execute("ALTER TABLE foods ADD COLUMN IF NOT EXISTS frequency VARCHAR(255)");
        FOOD_GUIDE.forEach((name, guide) -> jdbcTemplate.update(
                "UPDATE foods SET food_role = ?, frequency = ? WHERE name = ?",
                guide.role().name(), guide.frequency(), name));
        // Fix 15: fat profile (fat, monounsaturated, saturated, ALA) added to micro_breakdown for
        // the omega-9 and plant omega-3 badges. saturatedFatG is the canary.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE micro_breakdown IS NULL " +
                "OR micro_breakdown NOT LIKE '%\"saturatedFatG\":%'");
        // Fix 16: polyunsaturated fat added to micro_breakdown for the 'high monounsaturated fat'
        // claim (share of all fatty acids). polyunsaturatedFatG is the canary.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE micro_breakdown IS NULL " +
                "OR micro_breakdown NOT LIKE '%\"polyunsaturatedFatG\":%'");
        // Sync sequences past current max IDs so seedMissingFoods() inserts don't get
        // duplicate-key errors when the sequence drifted out of sync with existing rows.
        jdbcTemplate.execute(
                "SELECT setval('foods_id_seq', GREATEST(COALESCE((SELECT MAX(id) FROM foods), 0) + 1, 1), false)");
        jdbcTemplate.execute(
                "SELECT setval('nutrition_scores_id_seq', GREATEST(COALESCE((SELECT MAX(id) FROM nutrition_scores), 0) + 1, 1), false)");
    }

    private void seedFoods() {

        jdbcTemplate.execute("ALTER SEQUENCE foods_id_seq RESTART WITH 1");
        jdbcTemplate.execute("ALTER SEQUENCE nutrition_scores_id_seq RESTART WITH 1");

        List<Food> foods = new ArrayList<>();
        foods.add(createFood("Sardines", "FISH", 100));
        foods.add(createFood("Oats", "GRAIN", 100));
        foods.add(createFood("Garlic", "VEGETABLE", 10));
        foods.add(createFood("Eggs", "PROTEIN", 100));
        foods.add(createFood("Chicken breast", "PROTEIN", 100));
        foods.add(createFood("Beef mince 10%", "PROTEIN", 100));
        foods.add(createFood("Sweet potato", "VEGETABLE", 100));
        foods.add(createFood("Brown rice", "GRAIN", 100));
        foods.add(createFood("White rice", "GRAIN", 100));
        foods.add(createFood("Pearl barley", "GRAIN", 100));
        foods.add(createFood("Whole-wheat spaghetti", "GRAIN", 100));
        foods.add(createFood("Red lentils", "LEGUME", 100));
        foods.add(createFood("Green lentils", "LEGUME", 100));
        foods.add(createFood("Red kidney beans", "LEGUME", 100));
        foods.add(createFood("Peas", "VEGETABLE", 100));
        foods.add(createFood("Spinach", "VEGETABLE", 100));
        foods.add(createFood("Apple", "FRUIT", 100));
        foods.add(createFood("Banana", "FRUIT", 100));
        foods.add(createFood("Kiwi", "FRUIT", 100));
        foods.add(createFood("Blueberries", "FRUIT", 100));
        foods.add(createFood("Ginger", "VEGETABLE", 10));
        foods.add(createFood("Honey", "OTHER", 20));
        foods.add(createFood("Peanut butter", "OTHER", 30));
        foods.add(createFood("Tahini", "OTHER", 15));
        foods.add(createFood("Olive oil", "OTHER", 15));
        foods.add(createFood("Dark chocolate 70%", "OTHER", 30));
        foods.add(createFood("Salmon", "FISH", 100));
        foods.add(createFood("Greek yogurt", "DAIRY", 150));
        foods.add(createFood("Broccoli", "VEGETABLE", 80));
        foods.add(createFood("Avocado", "FRUIT", 100));
        foods.add(createFood("Quinoa", "GRAIN", 100));
        foods.add(createFood("Black beans", "LEGUME", 100));
        foods.add(createFood("Walnuts", "NUT", 30));
        foods.add(createFood("Cottage cheese", "DAIRY", 100));
        foods.add(createFood("Lemon", "FRUIT", 15));
        foods.add(createFood("Flaxseed", "SEED", 15));
        foods.add(createFood("Chia seeds", "SEED", 15));
        foods.add(createFood("Sweet corn", "VEGETABLE", 80));
        foods.add(createFood("Bell pepper", "VEGETABLE", 80));
        foods.add(createFood("Tomato", "VEGETABLE", 100));
        foods.add(createFood("Brazil nuts", "NUT", 10));

        for (Food food : foods) {
            nutritionScoreService.computeIfMissing(food);
        }
    }

    private void seedMissingFoods() {
        seedFoodIfMissing("Salmon", "FISH", 100);
        seedFoodIfMissing("Greek yogurt", "DAIRY", 150);
        seedFoodIfMissing("Broccoli", "VEGETABLE", 80);
        seedFoodIfMissing("Avocado", "FRUIT", 100);
        seedFoodIfMissing("Quinoa", "GRAIN", 100);
        seedFoodIfMissing("Black beans", "LEGUME", 100);
        seedFoodIfMissing("Walnuts", "NUT", 30);
        seedFoodIfMissing("Cottage cheese", "DAIRY", 100);
        seedFoodIfMissing("Lemon", "FRUIT", 15);
        seedFoodIfMissing("Flaxseed", "SEED", 15);
        seedFoodIfMissing("Chia seeds", "SEED", 15);
        seedFoodIfMissing("Sweet corn", "VEGETABLE", 80);
        seedFoodIfMissing("Bell pepper", "VEGETABLE", 80);
        seedFoodIfMissing("Tomato", "VEGETABLE", 100);
        seedFoodIfMissing("Brazil nuts", "NUT", 10);
    }

    private void seedFoodIfMissing(String name, String category, int servingSizeG) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM foods WHERE name = ?", Integer.class, name);
        if (count == null || count == 0) {
            Food food = createFood(name, category, servingSizeG);
            nutritionScoreService.computeIfMissing(food);
        }
    }

    private Food createFood(String name, String category, int servingSizeG) {
        Guide guide = FOOD_GUIDE.get(name);
        if (guide == null) {
            throw new IllegalStateException("No FOOD_GUIDE entry for seeded food '" + name + "'");
        }
        Food food = new Food();
        food.setName(name);
        food.setCategory(category);
        food.setFoodRole(guide.role());
        food.setFrequency(guide.frequency());
        food.setServingSizeG(servingSizeG);
        return foodRepository.save(food);
    }
}
