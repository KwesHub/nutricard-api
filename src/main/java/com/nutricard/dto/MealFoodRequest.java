package com.nutricard.dto;

import lombok.Data;

@Data
public class MealFoodRequest {
    private Long foodId;
    private Integer quantityG;
}
