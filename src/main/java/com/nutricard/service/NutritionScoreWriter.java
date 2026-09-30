package com.nutricard.service;

import com.nutricard.model.NutritionScore;
import com.nutricard.repository.NutritionScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Its own bean so the transaction annotation goes through Spring's proxy (a call from inside
// NutritionScoreService itself would skip it).
@Component
@RequiredArgsConstructor
public class NutritionScoreWriter {

    private final NutritionScoreRepository nutritionScoreRepository;

    // Runs in its own transaction and commits straight away. If two threads save a score for the same
    // food, the unique food_id constraint rejects one, and because that only rolls back this small
    // transaction it can't mark the caller's transaction rollback-only and fail the whole request.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NutritionScore saveNow(NutritionScore score) {
        return nutritionScoreRepository.save(score);
    }
}
