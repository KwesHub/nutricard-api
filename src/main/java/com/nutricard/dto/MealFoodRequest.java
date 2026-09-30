package com.nutricard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MealFoodRequest {
    @NotNull
    private Long foodId;
    @NotNull
    @Positive
    private Integer quantityG;
}
