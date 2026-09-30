package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.NutritionScoreRepository;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionScoreServiceTest {

    private final NutritionScoreRepository scores = mock(NutritionScoreRepository.class);
    private final ScoringService scoring = mock(ScoringService.class);
    private final NutritionScoreService service = new NutritionScoreService(scores, scoring);

    private Food food(long id, String name) {
        Food f = new Food();
        f.setId(id);
        f.setName(name);
        return f;
    }

    private NutritionScore scoreFor(Food food) {
        NutritionScore s = new NutritionScore();
        s.setFood(food);
        return s;
    }

    @Test
    void getOrComputeReturnsTheSavedScoreWithoutComputing() {
        Food oats = food(1, "Oats");
        NutritionScore saved = scoreFor(oats);
        when(scores.findByFoodId(1L)).thenReturn(Optional.of(saved));

        assertSame(saved, service.getOrCompute(oats));

        verify(scoring, never()).calculateFromUsda(any());
        verify(scores, never()).save(any());
    }

    @Test
    void getOrComputeComputesAndSavesWhenThereIsNoSavedScore() {
        Food eggs = food(2, "Eggs");
        NutritionScore computed = scoreFor(eggs);
        when(scores.findByFoodId(2L)).thenReturn(Optional.empty());
        when(scoring.calculateFromUsda(eggs)).thenReturn(Optional.of(computed));
        when(scores.save(computed)).thenReturn(computed);

        assertSame(computed, service.getOrCompute(eggs));

        verify(scores).save(computed);
    }

    @Test
    void getOrComputeAllUsesOneQueryAndComputesOnlyTheMissingFoods() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        NutritionScore savedA = scoreFor(a);
        NutritionScore computedB = scoreFor(b);
        when(scores.findByFoodIdIn(any())).thenReturn(List.of(savedA));
        when(scoring.calculateFromUsda(b)).thenReturn(Optional.of(computedB));
        when(scores.save(computedB)).thenReturn(computedB);

        // A appears twice: the batch should not compute or query for it again
        Map<Long, NutritionScore> result = service.getOrComputeAll(List.of(a, b, a));

        assertEquals(Map.of(1L, savedA, 2L, computedB), result);
        verify(scores, times(1)).findByFoodIdIn(any());
        verify(scoring, times(1)).calculateFromUsda(any());
        verify(scores, times(1)).save(any());
    }

    @Test
    void computeIfMissingSavesANewScoreAndReportsIt() {
        Food eggs = food(2, "Eggs");
        NutritionScore computed = scoreFor(eggs);
        when(scores.findByFoodId(2L)).thenReturn(Optional.empty());
        when(scoring.calculateFromUsda(eggs)).thenReturn(Optional.of(computed));

        assertTrue(service.computeIfMissing(eggs));

        verify(scores).save(computed);
    }

    @Test
    void computeIfMissingDoesNothingWhenAScoreExists() {
        Food oats = food(1, "Oats");
        when(scores.findByFoodId(1L)).thenReturn(Optional.of(scoreFor(oats)));

        assertFalse(service.computeIfMissing(oats));

        verify(scoring, never()).calculateFromUsda(any());
    }

    // ---- USDA unavailable ----

    private void usdaDown(Food food) {
        when(scores.findByFoodId(food.getId())).thenReturn(Optional.empty());
        when(scoring.calculateFromUsda(food)).thenReturn(Optional.empty());
    }

    @Test
    void usdaDownServesTheBuiltInFallbackButNeverSavesIt() {
        Food sardines = food(3, "Sardines");
        NutritionScore fallback = scoreFor(sardines);
        usdaDown(sardines);
        when(scoring.hasFallback(sardines)).thenReturn(true);
        when(scoring.calculateFallback(sardines)).thenReturn(fallback);

        assertSame(fallback, service.getOrCompute(sardines));

        verify(scores, never()).save(any());
    }

    @Test
    void usdaDownWithNoFallbackIsA503AndSavesNothing() {
        Food eggs = food(2, "Eggs");
        usdaDown(eggs);
        when(scoring.hasFallback(eggs)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getOrCompute(eggs));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(scores, never()).save(any());
    }

    @Test
    void aBatchWithAFoodThatHasNoDataAndNoFallbackFailsInsteadOfSavingZeros() {
        Food a = food(1, "A");
        Food b = food(2, "B");
        when(scores.findByFoodIdIn(any())).thenReturn(List.of(scoreFor(a)));
        when(scoring.calculateFromUsda(b)).thenReturn(Optional.empty());
        when(scoring.hasFallback(b)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> service.getOrComputeAll(List.of(a, b)));

        verify(scores, never()).save(any());
    }

    @Test
    void computeIfMissingSavesNothingWhenUsdaIsDown() {
        Food eggs = food(2, "Eggs");
        usdaDown(eggs);

        assertFalse(service.computeIfMissing(eggs));

        verify(scores, never()).save(any());
    }
}
