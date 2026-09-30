package com.nutricard.service;

import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.repository.NutritionScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// What a USDA outage does end to end: real FoodService, NutritionScoreService and ScoringService on
// a real Hibernate session; only the USDA client is faked, and it returns no data (its mock default).
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({FoodService.class, NutritionScoreService.class, NutritionScoreWriter.class, ScoringService.class})
class FoodServiceOutageJpaTest {

    @Autowired
    private FoodService foodService;
    @Autowired
    private NutritionScoreRepository scores;
    @Autowired
    private TestEntityManager em;

    @MockBean
    private NutrientDataService usda;

    private Food saveFood(String name, FoodRole role) {
        Food f = new Food();
        f.setName(name);
        f.setCategory("OTHER");
        f.setServingSizeG(100);
        f.setFoodRole(role);
        return em.persistAndFlush(f);
    }

    @Test
    void foodWithAFallbackStillGetsACardButNothingIsSaved() {
        // Garlic is PANTRY, so this also runs the detach line on a score that was never saved
        Food garlic = saveFood("Garlic", FoodRole.PANTRY);

        FoodCardResponse card = foodService.getCard(garlic.getId());

        assertEquals(10.0, card.getNutritionScore().getProteinQuality(), 0.001);
        assertNull(card.getNutritionScore().getTimingScores());
        em.flush();
        assertTrue(scores.findByFoodId(garlic.getId()).isEmpty(), "a fallback must not be saved");
    }

    @Test
    void foodWithNoFallbackIsA503AndNothingIsSaved() {
        Food eggs = saveFood("Eggs", FoodRole.DAILY_DRIVER);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> foodService.getCard(eggs.getId()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(scores.findByFoodId(eggs.getId()).isEmpty(), "zeros must not be saved");
    }
}
