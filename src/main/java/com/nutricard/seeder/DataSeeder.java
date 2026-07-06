package com.nutricard.seeder;

import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import com.nutricard.service.ScoringService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;
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
                if (nutritionScoreRepository.findByFoodId(food.getId()).isPresent()) continue;
                try {
                    nutritionScoreRepository.save(scoringService.calculateScores(food));
                    computed++;
                } catch (Exception e) {
                    log.warn("Score warm-up failed for '{}': {}", food.getName(), e.getMessage());
                }
            }
            if (computed > 0) log.info("Score warm-up computed {} missing scores", computed);
        }, "score-warmup");
        warmup.setDaemon(true);
        warmup.start();
    }

    private void applyMigrations() {
        // Fix 1: White rice was seeded as PANTRY; correct the role
        jdbcTemplate.update(
                "UPDATE foods SET food_role = 'DAILY_DRIVER' WHERE name = 'White rice' AND food_role = 'PANTRY'");
        // Drop white rice score if timing_scores is null (it was computed while role was PANTRY)
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'White rice') AND timing_scores IS NULL");
        // Fix 2: Drop green lentils score computed from dry-weight USDA data (protein_quality > 55
        // identifies the inflated score; corrected cooked values give protein_quality ≈ 39)
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'Green lentils') AND protein_quality > 55");
        // Fix 3: Micronutrient scoring changed to per-100-kcal basis (divisor 5.0 → 1.2).
        // Drop all scores so they recompute lazily. Canary: peanut butter scores >75 under old
        // per-100g formula and ~58.8 under new per-kcal formula — becomes a no-op after one rescore.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE EXISTS (" +
                "  SELECT 1 FROM nutrition_scores ns2" +
                "  JOIN foods f ON f.id = ns2.food_id" +
                "  WHERE f.name = 'Peanut butter' AND ns2.micronutrient_density > 75)");
        // Fix 4: Omega-3 gut bonus added. Drop sardines score with gut_health = 0 so it recomputes
        // and picks up the EPA+DHA bonus. Canary: gut_health = 0 is the pre-bonus value.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'Sardines') AND gut_health = 0");
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
        // Fix 7: Rarity weighting changed from redistribution (abundant nutrients discounted
        // below baseline) to pure bonus (abundant nutrients keep full weight), with the divisor
        // recalibrated 1.2 -> 1.31 to hold the peanut butter ~58.8 anchor. Canary: peanut
        // butter scored ~50 under the discount scheme and ~64 under the uncalibrated bonus
        // scheme; ~58.8 after this fix, so the 55-62 window makes it a no-op after one rescore.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE EXISTS (" +
                "  SELECT 1 FROM nutrition_scores ns2" +
                "  JOIN foods f ON f.id = ns2.food_id" +
                "  WHERE f.name = 'Peanut butter'" +
                "  AND (ns2.micronutrient_density < 55 OR ns2.micronutrient_density > 62))");
        // Fix 8: Lemon's FDC ID pointed at pork backribs (168299) and Sweet corn's at cilantro
        // leaves (169997) — badges surfaced both. IDs corrected to 167746 / 169998. Canary:
        // the wrong entries scored ~224 kcal for lemon (real: ~29) and ~23 kcal for corn
        // (real: ~86), so the kcal windows identify poisoned scores and then become no-ops.
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'Lemon') AND kcal_per100g > 100");
        jdbcTemplate.update(
                "DELETE FROM nutrition_scores WHERE food_id IN (SELECT id FROM foods WHERE name = 'Sweet corn') AND kcal_per100g < 50");
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
        foods.add(createFood("Sardines", "FISH", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Oats", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Garlic", "VEGETABLE", FoodRole.PANTRY, 10));
        foods.add(createFood("Eggs", "PROTEIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Chicken breast", "PROTEIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Beef mince 10%", "PROTEIN", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Sweet potato", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Brown rice", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("White rice", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Pearl barley", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Whole-wheat spaghetti", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Red lentils", "LEGUME", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Green lentils", "LEGUME", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Red kidney beans", "LEGUME", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Peas", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Spinach", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Apple", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Banana", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Kiwi", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Blueberries", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Ginger", "VEGETABLE", FoodRole.BOOSTER, 10));
        foods.add(createFood("Honey", "OTHER", FoodRole.OCCASIONAL, 20));
        foods.add(createFood("Peanut butter", "OTHER", FoodRole.BOOSTER, 30));
        foods.add(createFood("Tahini", "OTHER", FoodRole.PANTRY, 15));
        foods.add(createFood("Olive oil", "OTHER", FoodRole.PANTRY, 15));
        foods.add(createFood("Dark chocolate 70%", "OTHER", FoodRole.OCCASIONAL, 30));
        foods.add(createFood("Salmon", "FISH", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Greek yogurt", "DAIRY", FoodRole.DAILY_DRIVER, 150));
        foods.add(createFood("Broccoli", "VEGETABLE", FoodRole.DAILY_DRIVER, 80));
        foods.add(createFood("Avocado", "FRUIT", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Quinoa", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Black beans", "LEGUME", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Walnuts", "NUT", FoodRole.BOOSTER, 30));
        foods.add(createFood("Cottage cheese", "DAIRY", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Lemon", "FRUIT", FoodRole.PANTRY, 15));
        foods.add(createFood("Flaxseed", "SEED", FoodRole.BOOSTER, 15));
        foods.add(createFood("Chia seeds", "SEED", FoodRole.BOOSTER, 15));
        foods.add(createFood("Sweet corn", "VEGETABLE", FoodRole.DAILY_DRIVER, 80));
        foods.add(createFood("Bell pepper", "VEGETABLE", FoodRole.DAILY_DRIVER, 80));
        foods.add(createFood("Tomato", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Brazil nuts", "NUT", FoodRole.BOOSTER, 10));

        for (Food food : foods) {
            NutritionScore score = scoringService.calculateScores(food);
            nutritionScoreRepository.save(score);
        }
    }

    private void seedMissingFoods() {
        seedFoodIfMissing("Salmon", "FISH", FoodRole.WEEKLY_ANCHOR, 100);
        seedFoodIfMissing("Greek yogurt", "DAIRY", FoodRole.DAILY_DRIVER, 150);
        seedFoodIfMissing("Broccoli", "VEGETABLE", FoodRole.DAILY_DRIVER, 80);
        seedFoodIfMissing("Avocado", "FRUIT", FoodRole.DAILY_DRIVER, 100);
        seedFoodIfMissing("Quinoa", "GRAIN", FoodRole.DAILY_DRIVER, 100);
        seedFoodIfMissing("Black beans", "LEGUME", FoodRole.DAILY_DRIVER, 100);
        seedFoodIfMissing("Walnuts", "NUT", FoodRole.BOOSTER, 30);
        seedFoodIfMissing("Cottage cheese", "DAIRY", FoodRole.DAILY_DRIVER, 100);
        seedFoodIfMissing("Lemon", "FRUIT", FoodRole.PANTRY, 15);
        seedFoodIfMissing("Flaxseed", "SEED", FoodRole.BOOSTER, 15);
        seedFoodIfMissing("Chia seeds", "SEED", FoodRole.BOOSTER, 15);
        seedFoodIfMissing("Sweet corn", "VEGETABLE", FoodRole.DAILY_DRIVER, 80);
        seedFoodIfMissing("Bell pepper", "VEGETABLE", FoodRole.DAILY_DRIVER, 80);
        seedFoodIfMissing("Tomato", "VEGETABLE", FoodRole.DAILY_DRIVER, 100);
        seedFoodIfMissing("Brazil nuts", "NUT", FoodRole.BOOSTER, 10);
    }

    private void seedFoodIfMissing(String name, String category, FoodRole role, int servingSizeG) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM foods WHERE name = ?", Integer.class, name);
        if (count == null || count == 0) {
            Food food = createFood(name, category, role, servingSizeG);
            NutritionScore score = scoringService.calculateScores(food);
            nutritionScoreRepository.save(score);
        }
    }

    private Food createFood(String name, String category, FoodRole role, int servingSizeG) {
        Food food = new Food();
        food.setName(name);
        food.setCategory(category);
        food.setFoodRole(role);
        food.setServingSizeG(servingSizeG);
        return foodRepository.save(food);
    }
}
