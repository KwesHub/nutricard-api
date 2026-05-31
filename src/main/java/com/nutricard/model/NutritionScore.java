package com.nutricard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nutrition_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "food_id", nullable = false, unique = true)
    private Food food;

    private Double proteinQuality;

    private Double micronutrientDensity;

    private Double energyProfile;

    private Double gutHealth;

    private Double phytonutrients;

    private Double bioavailabilityModifier;

    private Double overallScore;
}
