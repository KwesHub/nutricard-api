package com.nutricard.model;

// The food's role on the plate. How often to eat it is a separate field (Food.frequency),
// because "2-3x a week" mixed two ideas: a minimum (oily fish) and a ceiling (red meat).
public enum FoodRole {
    BASE,
    PROTEIN,
    VEG_FRUIT,
    BOOSTER,
    FLAVOUR,
    TREAT
}
