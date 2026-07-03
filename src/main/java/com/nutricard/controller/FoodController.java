package com.nutricard.controller;

import com.nutricard.dto.CompareResponse;
import com.nutricard.dto.FoodCardResponse;
import com.nutricard.model.Food;
import com.nutricard.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    public List<Food> getAllFoods(@RequestParam(required = false) String search) {
        return foodService.getAll(search);
    }

    @GetMapping("/compare")
    public CompareResponse compareFoods(@RequestParam Long a, @RequestParam Long b) {
        return foodService.compare(a, b);
    }

    @GetMapping("/{id}/card")
    public FoodCardResponse getFoodCard(@PathVariable Long id) {
        return foodService.getCard(id);
    }
}
