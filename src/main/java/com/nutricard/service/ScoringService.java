package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.service.NutrientDataService.NutrientData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScoringService {

    private final NutrientDataService nutrientDataService;

    public NutritionScore calculateScores(Food food) {
        NutrientData data = nutrientDataService.fetchNutrientData(food.getName());

        if (data != null) {
            return calculateFromRealData(food, data);
        }

        return calculateFallback(food);
    }

    private NutritionScore calculateFromRealData(Food food, NutrientData data) {
        NutritionScore score = new NutritionScore();
        score.setFood(food);

        double proteinQuality = Math.min((data.proteins100g() / 35.0) * 100, 100);
        double micronutrientDensity = ((data.vitaminCount() + data.mineralCount()) / 13.0) * 100;
        double energyProfile = Math.max(0, 100 - (data.energyKcal100g() / 9.0));
        double gutHealth = Math.min((data.fiber100g() / 10.0) * 100, 100);
        double phytonutrients = data.omega3Fat100g() > 0 ? 80.0 : 40.0;
        double bioavailabilityModifier = 1.0;

        score.setProteinQuality(round(proteinQuality));
        score.setMicronutrientDensity(round(micronutrientDensity));
        score.setEnergyProfile(round(energyProfile));
        score.setGutHealth(round(gutHealth));
        score.setPhytonutrients(round(phytonutrients));
        score.setBioavailabilityModifier(bioavailabilityModifier);

        double overallScore = calculateOverallScore(food.getFoodRole(),
                proteinQuality, micronutrientDensity, energyProfile, gutHealth, phytonutrients);
        score.setOverallScore(round(overallScore));

        return score;
    }

    private double calculateOverallScore(FoodRole role,
                                          double protein, double micronutrient,
                                          double energy, double gut, double phyto) {
        return switch (role) {
            case WEEKLY_ANCHOR ->
                    protein * 0.30 + micronutrient * 0.35 + energy * 0.10 + gut * 0.10 + phyto * 0.15;
            case PANTRY ->
                    protein * 0.05 + micronutrient * 0.20 + energy * 0.05 + gut * 0.30 + phyto * 0.40;
            default ->
                    protein * 0.25 + micronutrient * 0.20 + energy * 0.30 + gut * 0.15 + phyto * 0.10;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

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
