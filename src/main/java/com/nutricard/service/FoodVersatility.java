package com.nutricard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Versatility = how well a food works as a base that carries other flavours (white rice can take
// almost anything). Scored from three stated traits per food rather than a hand-picked number, so
// every score can be explained. The traits are editorial judgements; the formula is fixed:
//   flavour  neutral 40, mild 25, strong 5
//   taste    sweet and savoury 20, one of them 10
//   plate    base of a meal 40, side 20, accent 5
// Recipe counts were tried and rejected: they measure "appears as an ingredient", which ranks eggs
// (baking, batters, sauces) far above rice (TheMealDB: 234 vs 45), the opposite of this meaning.
public final class FoodVersatility {

    public enum Flavour { NEUTRAL, MILD, STRONG }
    public enum Plate { BASE, SIDE, ACCENT }

    public record Traits(Flavour flavour, boolean sweet, boolean savoury, Plate plate) {}

    public record Versatility(int score, List<String> reasons) {}

    private static Traits t(Flavour f, boolean sweet, boolean savoury, Plate p) {
        return new Traits(f, sweet, savoury, p);
    }

    private static final Flavour N = Flavour.NEUTRAL, M = Flavour.MILD, S = Flavour.STRONG;
    private static final Plate B = Plate.BASE, D = Plate.SIDE, A = Plate.ACCENT;

    static final Map<String, Traits> TRAITS = Map.ofEntries(
            Map.entry("White rice", t(N, true, true, B)),
            Map.entry("Brown rice", t(M, false, true, B)),
            Map.entry("Oats", t(M, true, true, B)),
            Map.entry("Pearl barley", t(M, false, true, B)),
            Map.entry("Whole-wheat spaghetti", t(M, false, true, B)),
            Map.entry("Quinoa", t(M, true, true, B)),
            Map.entry("Sweet potato", t(M, true, true, B)),
            Map.entry("Red lentils", t(M, false, true, B)),
            Map.entry("Green lentils", t(M, false, true, B)),
            Map.entry("Red kidney beans", t(M, false, true, B)),
            Map.entry("Black beans", t(M, false, true, B)),
            Map.entry("Beef mince 10%", t(M, false, true, B)),
            Map.entry("Greek yogurt", t(M, true, true, B)),
            Map.entry("Chicken breast", t(N, false, true, D)),
            Map.entry("Eggs", t(M, true, true, D)),
            Map.entry("Cottage cheese", t(M, true, true, D)),
            Map.entry("Salmon", t(M, false, true, D)),
            Map.entry("Sardines", t(S, false, true, D)),
            Map.entry("Spinach", t(M, true, true, D)),
            Map.entry("Broccoli", t(M, false, true, D)),
            Map.entry("Peas", t(M, false, true, D)),
            Map.entry("Sweet corn", t(M, false, true, D)),
            Map.entry("Bell pepper", t(M, false, true, D)),
            Map.entry("Tomato", t(M, false, true, D)),
            Map.entry("Avocado", t(M, true, true, D)),
            Map.entry("Apple", t(M, true, true, D)),
            Map.entry("Banana", t(M, true, false, D)),
            Map.entry("Kiwi", t(M, true, false, D)),
            Map.entry("Blueberries", t(M, true, false, A)),
            Map.entry("Flaxseed", t(N, true, true, A)),
            Map.entry("Chia seeds", t(N, true, false, A)),
            Map.entry("Walnuts", t(M, true, true, A)),
            Map.entry("Peanut butter", t(M, true, true, A)),
            Map.entry("Tahini", t(M, true, true, A)),
            Map.entry("Brazil nuts", t(M, true, false, A)),
            Map.entry("Olive oil", t(M, true, true, A)),
            Map.entry("Honey", t(M, true, true, A)),
            Map.entry("Garlic", t(S, false, true, A)),
            Map.entry("Ginger", t(S, true, true, A)),
            Map.entry("Lemon", t(S, true, true, A)),
            Map.entry("Dark chocolate 70%", t(S, true, false, A))
    );

    private FoodVersatility() {}

    // Null for a food with no traits recorded (FoodMapsConsistencyTest makes that a test failure)
    public static Versatility of(String name) {
        Traits tr = TRAITS.get(name);
        if (tr == null) return null;
        List<String> reasons = new ArrayList<>();
        int score = switch (tr.flavour()) {
            case NEUTRAL -> { reasons.add("neutral flavour that takes on others"); yield 40; }
            case MILD -> { reasons.add("mild flavour"); yield 25; }
            case STRONG -> { reasons.add("strong flavour of its own"); yield 5; }
        };
        if (tr.sweet() && tr.savoury()) {
            reasons.add("works sweet and savoury");
            score += 20;
        } else {
            reasons.add(tr.sweet() ? "mostly sweet dishes" : "mostly savoury dishes");
            score += 10;
        }
        score += switch (tr.plate()) {
            case BASE -> { reasons.add("can be the base of a meal"); yield 40; }
            case SIDE -> { reasons.add("a main part or side of a meal"); yield 20; }
            case ACCENT -> { reasons.add("used in small amounts"); yield 5; }
        };
        return new Versatility(score, reasons);
    }
}
