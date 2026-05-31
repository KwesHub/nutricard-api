package com.nutricard.seeder;

import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import com.nutricard.service.ScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final FoodRepository foodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;
    private final ScoringService scoringService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("ALTER SEQUENCE foods_id_seq RESTART WITH 1");
        jdbcTemplate.execute("ALTER SEQUENCE nutrition_scores_id_seq RESTART WITH 1");

        if (foodRepository.count() > 0) {
            return;
        }

        List<Food> foods = new ArrayList<>();
        foods.add(createFood("Sardines", "FISH", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Oats", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Garlic", "VEGETABLE", FoodRole.PANTRY, 10));
        foods.add(createFood("Eggs", "PROTEIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Chicken breast", "PROTEIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Beef mince 10%", "PROTEIN", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Sweet potato", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Brown rice", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("White rice", "GRAIN", FoodRole.PANTRY, 100));
        foods.add(createFood("Pearl barley", "GRAIN", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Whole-wheat spaghetti", "GRAIN", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Red lentils", "LEGUME", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Green lentils", "LEGUME", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Red kidney beans", "LEGUME", FoodRole.WEEKLY_ANCHOR, 100));
        foods.add(createFood("Peas", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Spinach", "VEGETABLE", FoodRole.DAILY_DRIVER, 100));
        foods.add(createFood("Apple", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Banana", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Kiwi", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Blueberries", "FRUIT", FoodRole.BOOSTER, 100));
        foods.add(createFood("Ginger", "VEGETABLE", FoodRole.BOOSTER, 10));
        foods.add(createFood("Honey", "OTHER", FoodRole.OCCASIONAL, 20));
        foods.add(createFood("Peanut butter", "OTHER", FoodRole.WEEKLY_ANCHOR, 30));
        foods.add(createFood("Tahini", "OTHER", FoodRole.PANTRY, 15));
        foods.add(createFood("Olive oil", "OTHER", FoodRole.PANTRY, 15));
        foods.add(createFood("Dark chocolate 70%", "OTHER", FoodRole.OCCASIONAL, 30));

        for (Food food : foods) {
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
