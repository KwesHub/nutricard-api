package com.nutricard.controller;

import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.dto.FoodListItem;
import com.nutricard.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Routing only: each endpoint delegates to FoodService, which holds the logic.
@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public List<FoodListItem> getAllFoods(@RequestParam(required = false) String search) {
        return foodService.getAll(search);
    }

    @GetMapping("/compare")
    public CompareResponse compareFoods(@RequestParam Long a, @RequestParam Long b) {
        return foodService.compare(a, b);
    }

    // First request for a food computes and saves its score. That happens in the service.
    @GetMapping("/{id}/card")
    public FoodCardResponse getFoodCard(@PathVariable Long id) {
        return foodService.getCard(id);
    }
}
