package com.nutricard.dto;

// A small chip shown on food tiles and cards. kind is one of:
//   "strength" — nutrient covering >= 50% RDA per 100g (label is the nutrient key)
//   "rare"     — same, but the nutrient is a shortfall nutrient (label is the nutrient key)
//   "watch"    — anti-nutrient caution (label is display text, e.g. "Oxalates")
// detail is optional explainer text shown in a popover on click (what the anti-nutrient
// is and how to reduce it; why a rare nutrient matters). Null for plain strengths.
public record Badge(String label, String kind, String detail) {}
