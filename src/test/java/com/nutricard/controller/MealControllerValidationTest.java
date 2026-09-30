package com.nutricard.controller;

import com.nutricard.service.MealService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MealController.class)
class MealControllerValidationTest {

    @Autowired
    private MockMvc mvc;

    // The controller only needs a service to exist; validation fails before it is called
    @MockBean
    private MealService mealService;

    private void postMeal(String json, int expectedStatus) throws Exception {
        mvc.perform(post("/meals").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void emptyFoodListIsRejectedWithFieldName() throws Exception {
        mvc.perform(post("/meals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m\",\"timingContext\":\"NEUTRAL\",\"foods\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.foods").exists());
    }

    @Test
    void zeroGramsIsRejected() throws Exception {
        postMeal("{\"name\":\"m\",\"timingContext\":\"NEUTRAL\","
                + "\"foods\":[{\"foodId\":1,\"quantityG\":0}]}", 400);
    }

    @Test
    void missingTimingContextIsRejected() throws Exception {
        postMeal("{\"name\":\"m\",\"foods\":[{\"foodId\":1,\"quantityG\":50}]}", 400);
    }

    @Test
    void validMealPasses() throws Exception {
        postMeal("{\"name\":\"m\",\"timingContext\":\"NEUTRAL\","
                + "\"foods\":[{\"foodId\":1,\"quantityG\":50}]}", 200);
    }
}
