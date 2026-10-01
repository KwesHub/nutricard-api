package com.nutricard.service;

import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.NutritionScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These use a real Hibernate session on an in-memory H2 database, because the bugs they guard
// (dirty checking, the derived query) can't be reproduced with mocks.
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({FoodService.class, NutritionScoreService.class, NutritionScoreWriter.class})
class FoodServiceJpaTest {

    private static final String TIMING = "{\"MORNING\":50.0,\"NEUTRAL\":60.0}";

    @Autowired
    private FoodService foodService;
    @Autowired
    private NutritionScoreRepository scores;
    @Autowired
    private TestEntityManager em;

    // scoring itself is tested elsewhere; here it only needs to exist
    @MockBean
    private ScoringService scoring;

    private Food saveFood(String name, FoodRole role) {
        Food f = new Food();
        f.setName(name);
        f.setCategory("OTHER");
        f.setServingSizeG(100);
        f.setFoodRole(role);
        return em.persist(f);
    }

    private NutritionScore saveScore(Food food) {
        NutritionScore s = new NutritionScore();
        s.setFood(food);
        s.setProteinQuality(50.0);
        s.setTimingScores(TIMING);
        return em.persist(s);
    }

    @Test
    void pantryCardHidesTimingScoresWithoutWipingThemFromTheDatabase() {
        Food garlic = saveFood("Garlic", FoodRole.FLAVOUR);
        NutritionScore saved = saveScore(garlic);
        em.flush();
        em.clear();

        FoodCardResponse card = foodService.getCard(garlic.getId());

        assertNull(card.getNutritionScore().getTimingScores(), "the response should hide timing scores");

        // Without entityManager.detach in getCard, this flush writes the null to the database
        em.flush();
        em.clear();
        assertEquals(TIMING, em.find(NutritionScore.class, saved.getId()).getTimingScores(),
                "the stored timing scores must survive, meal scoring needs them");
    }

    @Test
    void nonPantryCardKeepsItsTimingScores() {
        Food oats = saveFood("Oats", FoodRole.BASE);
        saveScore(oats);
        em.flush();
        em.clear();

        FoodCardResponse card = foodService.getCard(oats.getId());

        assertEquals(TIMING, card.getNutritionScore().getTimingScores());
    }

    // ---- the batch query used by MealScoringService ----

    @Test
    void findByFoodIdInReturnsOnlyTheRequestedFoods() {
        Food a = saveFood("A", FoodRole.BASE);
        Food b = saveFood("B", FoodRole.BASE);
        Food c = saveFood("C", FoodRole.BASE);
        saveScore(a);
        saveScore(b);
        saveScore(c);
        em.flush();
        em.clear();

        List<NutritionScore> found = scores.findByFoodIdIn(List.of(a.getId(), c.getId()));

        assertEquals(2, found.size());
        assertTrue(found.stream().map(s -> s.getFood().getId()).toList().containsAll(List.of(a.getId(), c.getId())));
    }

    @Test
    void findByFoodIdInWithNoIdsReturnsNothingInsteadOfFailing() {
        assertNotNull(scores.findByFoodIdIn(List.of()));
        assertTrue(scores.findByFoodIdIn(List.of()).isEmpty());
    }
}
