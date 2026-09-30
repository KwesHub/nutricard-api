package com.nutricard.dto;

import com.nutricard.model.TimingContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateMealRequest {
    @NotBlank
    private String name;
    @NotNull
    private TimingContext timingContext;
    @NotEmpty
    private List<@Valid MealFoodRequest> foods;
}
