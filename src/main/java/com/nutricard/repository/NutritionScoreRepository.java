package com.nutricard.repository;

import com.nutricard.model.NutritionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NutritionScoreRepository extends JpaRepository<NutritionScore, Long> {
    Optional<NutritionScore> findByFoodId(Long foodId);

    List<NutritionScore> findByFoodIdIn(Collection<Long> foodIds);
}
