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

        Food sardines = new Food();
        sardines.setName("Sardines");
        sardines.setCategory("FISH");
        sardines.setFoodRole(FoodRole.WEEKLY_ANCHOR);
        sardines.setServingSizeG(100);
        sardines = foodRepository.save(sardines);

        Food oats = new Food();
        oats.setName("Oats");
        oats.setCategory("GRAIN");
        oats.setFoodRole(FoodRole.DAILY_DRIVER);
        oats.setServingSizeG(100);
        oats = foodRepository.save(oats);

        Food garlic = new Food();
        garlic.setName("Garlic");
        garlic.setCategory("VEGETABLE");
        garlic.setFoodRole(FoodRole.PANTRY);
        garlic.setServingSizeG(10);
        garlic = foodRepository.save(garlic);

        for (Food food : new Food[]{sardines, oats, garlic}) {
            NutritionScore score = scoringService.calculateScores(food);
            nutritionScoreRepository.save(score);
        }
    }
}
