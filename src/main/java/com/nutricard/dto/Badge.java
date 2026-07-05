package com.nutricard.dto;

// A small chip shown on food tiles and cards. kind is one of:
//   "strength" — nutrient covering >= 50% RDA per 100g (label is the nutrient key)
//   "rare"     — same, but the nutrient is a shortfall nutrient (label is the nutrient key)
//   "watch"    — anti-nutrient caution (label is display text, e.g. "Oxalates")
public record Badge(String label, String kind) {}
