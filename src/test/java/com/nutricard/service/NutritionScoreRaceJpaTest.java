package com.nutricard.service;

import com.nutricard.model.Food;
import com.nutricard.model.FoodRole;
import com.nutricard.model.NutritionScore;
import com.nutricard.repository.FoodRepository;
import com.nutricard.repository.NutritionScoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Real threads on a real database. NOT_SUPPORTED means no test-wide transaction, so every save is
// really committed and the threads genuinely race, like the startup warm-up against a request.
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({NutritionScoreService.class, NutritionScoreWriter.class})
class NutritionScoreRaceJpaTest {

    @Autowired
    private NutritionScoreService service;
    @Autowired
    private FoodRepository foods;
    @Autowired
    private NutritionScoreRepository scores;
    @Autowired
    private NutritionScoreWriter writer;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ScoringService scoring;

    @AfterEach
    void cleanUp() {
        scores.deleteAll();
        foods.deleteAll();
    }

    private Food saveFood(String name) {
        Food f = new Food();
        f.setName(name);
        f.setCategory("OTHER");
        f.setServingSizeG(100);
        f.setFoodRole(FoodRole.DAILY_DRIVER);
        return foods.save(f);
    }

    @Test
    void manyThreadsComputingTheSameMissingScoreAllGetOneAndOnlyOneRowIsSaved() throws Exception {
        Food eggs = saveFood("Eggs");
        // the pause makes every thread see "no saved score" before any of them has saved one
        when(scoring.calculateFromUsda(any(Food.class))).thenAnswer(inv -> {
            Thread.sleep(300);
            NutritionScore s = new NutritionScore();
            s.setFood(inv.getArgument(0));
            s.setProteinQuality(50.0);
            return Optional.of(s);
        });

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<NutritionScore>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.getOrCompute(eggs);
            }));
        }
        start.countDown();

        for (Future<NutritionScore> result : results) {
            assertNotNull(result.get(20, TimeUnit.SECONDS).getId(), "every caller should get the saved score");
        }
        pool.shutdown();
        assertEquals(1, scores.count());
    }

    @Test
    void aMissingScoreIsComputedAndCommittedInItsOwnTransaction() {
        Food eggs = saveFood("Eggs");
        NutritionScore computed = new NutritionScore();
        computed.setFood(eggs);
        computed.setProteinQuality(90.0);
        when(scoring.calculateFromUsda(any(Food.class))).thenReturn(Optional.of(computed));

        NutritionScore result = service.getOrCompute(eggs);

        assertEquals(90.0, result.getProteinQuality());
        assertTrue(scores.findByFoodId(eggs.getId()).isPresent(), "the score should be committed to the database");
    }

    @Test
    void aUniqueClashWhileSavingDoesNotBreakTheCallersTransaction() {
        Food eggs = saveFood("Eggs");
        NutritionScore winner = new NutritionScore();
        winner.setFood(eggs);
        winner.setProteinQuality(1.0);
        scores.save(winner); // another thread got there first
        NutritionScore loser = new NutritionScore();
        loser.setFood(eggs);

        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        Long foundId = caller.execute(status -> {
            assertThrows(DataIntegrityViolationException.class, () -> writer.saveNow(loser));
            // the caller's own transaction is still usable and can read the winner's row.
            // With the plain repository save (no separate transaction) the whole caller fails here.
            return scores.findByFoodId(eggs.getId()).orElseThrow().getId();
        });

        assertEquals(winner.getId(), foundId);
    }
}
