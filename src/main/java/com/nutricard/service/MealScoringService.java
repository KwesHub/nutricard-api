package com.nutricard.service;

import com.nutricard.model.*;
import com.nutricard.repository.MealFoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealScoringService {

    private final MealFoodRepository mealFoodRepository;
    private final NutritionScoreRepository nutritionScoreRepository;

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
                    .orElse(null);
            if (ns == null) continue;

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

        boolean hasOmega3Fish = foodNames.stream().anyMatch(n -> n.equals("Sardines") || n.equals("Mackerel"));
        boolean hasAllium = foodNames.stream().anyMatch(n -> n.equals("Garlic") || n.equals("Onion"));
        boolean hasOats = foodNames.contains("Oats");
        boolean hasVitC = foodNames.stream().anyMatch(n -> n.equals("Kiwi") || n.equals("Lemon") || n.equals("Orange"));
        boolean hasSpinach = foodNames.contains("Spinach");
        boolean hasVitCForIron = foodNames.stream().anyMatch(n -> n.equals("Lemon") || n.equals("Kiwi"));

        if (hasOmega3Fish && hasAllium) {
            synergies.add("Omega-3 + Allicin: anti-inflammatory combination");
        }

        if (hasOats && hasVitC) {
            synergies.add("Vitamin C reduces phytic acid effect in oats — improved mineral absorption");
        }

        if (hasSpinach && hasVitCForIron) {
            synergies.add("Vitamin C improves iron absorption from spinach");
        }

        boolean hasHighProtein = foodsWithScores.stream()
                .anyMatch(fs -> fs.score().getProteinQuality() > 60);
        boolean hasHighGut = foodsWithScores.stream()
                .anyMatch(fs -> fs.score().getGutHealth() > 60);
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
