package com.nutricard.repository;

import com.nutricard.model.MealScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MealScoreRepository extends JpaRepository<MealScore, Long> {
    Optional<MealScore> findByMealId(Long mealId);
}
