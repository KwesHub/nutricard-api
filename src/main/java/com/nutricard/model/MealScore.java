package com.nutricard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meal_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "meal_id", nullable = false, unique = true)
    private Meal meal;

    private Double proteinQuality;

    private Double micronutrientDensity;

    private Double energyProfile;

    private Double gutHealth;

    private Double phytonutrients;

    private Double bioavailabilityModifier;

    private Double overallScore;

    @Column(length = 1000)
    private String activeSynergies;
}
