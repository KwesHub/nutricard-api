package com.nutricard.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// Nutrient pairings: foods that help each other nutritionally. One list drives both the meal
// card ("active synergies") and the food card ("pairs well with"), so the two can't disagree.
// Partners for iron are only foods with a real vitamin C dose (about 50mg+ per portion): pepper,
// kiwi, broccoli. A squeeze of lemon is too little to matter.
public final class FoodPairings {

    public record Pairing(Set<String> left, Set<String> right, String reason) {
        boolean activeIn(Collection<String> names) {
            return names.stream().anyMatch(left::contains) && names.stream().anyMatch(right::contains);
        }
    }

    // For a single food: the foods it pairs with and why
    public record PairsWith(List<String> foods, String reason) {}

    private static final Set<String> VITAMIN_C_FOR_IRON = Set.of("Bell pepper", "Kiwi", "Broccoli");
    private static final Set<String> DIETARY_FAT = Set.of(
            "Olive oil", "Avocado", "Salmon", "Sardines", "Walnuts", "Flaxseed", "Chia seeds");

    static final List<Pairing> PAIRINGS = List.of(
            new Pairing(Set.of("Sardines", "Salmon"), Set.of("Garlic"),
                    "omega-3 and garlic are both studied for lowering inflammation"),
            new Pairing(Set.of("Oats"), VITAMIN_C_FOR_IRON,
                    "vitamin C offsets some of the iron-blocking effect of the phytic acid in oats"),
            new Pairing(Set.of("Spinach"), VITAMIN_C_FOR_IRON,
                    "vitamin C improves iron absorption from spinach"),
            new Pairing(Set.of("Red lentils", "Green lentils", "Red kidney beans", "Black beans"), VITAMIN_C_FOR_IRON,
                    "vitamin C improves iron absorption from beans and lentils"),
            new Pairing(Set.of("Tomato"), DIETARY_FAT,
                    "fat helps you absorb tomato's lycopene, up to about 4 times more"),
            new Pairing(Set.of("Sweet potato", "Spinach"), DIETARY_FAT,
                    "a little fat helps you absorb the beta-carotene your body turns into vitamin A")
    );

    private FoodPairings() {}

    public static List<String> activeIn(Collection<String> foodNames) {
        return PAIRINGS.stream().filter(p -> p.activeIn(foodNames)).map(Pairing::reason).toList();
    }

    public static List<PairsWith> forFood(String name) {
        List<PairsWith> out = new ArrayList<>();
        for (Pairing p : PAIRINGS) {
            if (p.left().contains(name)) {
                out.add(new PairsWith(p.right().stream().sorted().toList(), p.reason()));
            } else if (p.right().contains(name)) {
                out.add(new PairsWith(p.left().stream().sorted().toList(), p.reason()));
            }
        }
        return out;
    }
}
