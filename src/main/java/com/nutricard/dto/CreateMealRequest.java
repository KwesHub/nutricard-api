package com.nutricard.dto;

import com.nutricard.model.TimingContext;
import lombok.Data;

import java.util.List;

@Data
public class CreateMealRequest {
    private String name;
    private TimingContext timingContext;
    private List<MealFoodRequest> foods;
}
