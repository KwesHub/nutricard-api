package com.nutricard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nutricard.dto.Badge;
import com.nutricard.model.Food;
import com.nutricard.model.NutritionScore;
import com.nutricard.model.TimingContext;
import com.nutricard.service.NutrientDataService.NutrientData;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

// All the scoring rules live here, so the controllers stay thin.
@Service
@RequiredArgsConstructor
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final NutrientDataService nutrientDataService;

    // --- Lookup maps ---
    // Hand-picked values per food, keyed on the exact name.
    // A missing name quietly uses a default, so a typo won't throw an error.

    private static final Map<String, Double> PDCAAS_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.00), Map.entry("Chicken breast", 0.91),
            Map.entry("Beef mince 10%", 0.92), Map.entry("Sardines", 0.90),
            Map.entry("Red lentils", 0.52), Map.entry("Green lentils", 0.52),
            Map.entry("Red kidney beans", 0.68), Map.entry("Oats", 0.57),
            Map.entry("Brown rice", 0.50), Map.entry("White rice", 0.50),
            Map.entry("Pearl barley", 0.55), Map.entry("Whole-wheat spaghetti", 0.55),
            Map.entry("Peanut butter", 0.52), Map.entry("Peas", 0.65),
            Map.entry("Spinach", 0.70), Map.entry("Sweet potato", 0.70),
            Map.entry("Salmon", 0.94), Map.entry("Greek yogurt", 1.00),
            Map.entry("Broccoli", 0.60), Map.entry("Avocado", 0.60),
            Map.entry("Quinoa", 0.85), Map.entry("Black beans", 0.75),
            Map.entry("Walnuts", 0.50), Map.entry("Cottage cheese", 1.00),
            Map.entry("Lemon", 0.60), Map.entry("Flaxseed", 0.52),
            Map.entry("Chia seeds", 0.60), Map.entry("Sweet corn", 0.55),
            Map.entry("Bell pepper", 0.60), Map.entry("Tomato", 0.60),
            Map.entry("Brazil nuts", 0.45)
    );

    private static final Map<String, Double> COMPLETENESS_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.0), Map.entry("Chicken breast", 1.0),
            Map.entry("Beef mince 10%", 1.0), Map.entry("Sardines", 1.0),
            Map.entry("Peanut butter", 0.62), Map.entry("Red lentils", 0.72),
            Map.entry("Green lentils", 0.72), Map.entry("Red kidney beans", 0.68),
            Map.entry("Oats", 0.70), Map.entry("Brown rice", 0.65),
            Map.entry("White rice", 0.65), Map.entry("Pearl barley", 0.68),
            Map.entry("Whole-wheat spaghetti", 0.65), Map.entry("Peas", 0.75),
            Map.entry("Spinach", 0.70), Map.entry("Sweet potato", 0.70),
            Map.entry("Salmon", 1.0), Map.entry("Greek yogurt", 1.0),
            Map.entry("Broccoli", 0.65), Map.entry("Avocado", 0.60),
            Map.entry("Quinoa", 0.95), Map.entry("Black beans", 0.72),
            Map.entry("Walnuts", 0.60), Map.entry("Cottage cheese", 1.0),
            Map.entry("Lemon", 0.60), Map.entry("Flaxseed", 0.65),
            Map.entry("Chia seeds", 0.65), Map.entry("Sweet corn", 0.60),
            Map.entry("Bell pepper", 0.60), Map.entry("Tomato", 0.60),
            Map.entry("Brazil nuts", 0.60)
    );

    private static final Map<String, Double> BIOAVAILABILITY_MAP = Map.ofEntries(
            Map.entry("Eggs", 1.0), Map.entry("Sardines", 0.95),
            Map.entry("Chicken breast", 0.93), Map.entry("Beef mince 10%", 0.90),
            Map.entry("Sweet potato", 0.85), Map.entry("Kiwi", 0.90),
            Map.entry("Apple", 0.90), Map.entry("Banana", 0.90),
            Map.entry("Blueberries", 0.90), Map.entry("Spinach", 0.65),
            Map.entry("Red lentils", 0.75), Map.entry("Green lentils", 0.75),
            Map.entry("Red kidney beans", 0.75), Map.entry("Oats", 0.70),
            Map.entry("Brown rice", 0.70), Map.entry("Pearl barley", 0.70),
            Map.entry("Whole-wheat spaghetti", 0.72), Map.entry("White rice", 0.68),
            Map.entry("Peas", 0.78), Map.entry("Garlic", 0.85),
            Map.entry("Ginger", 0.85), Map.entry("Honey", 0.90),
            Map.entry("Peanut butter", 0.80), Map.entry("Tahini", 0.80),
            Map.entry("Olive oil", 0.85), Map.entry("Dark chocolate 70%", 0.80),
            Map.entry("Salmon", 0.95), Map.entry("Greek yogurt", 1.0),
            Map.entry("Broccoli", 0.80), Map.entry("Avocado", 0.90),
            Map.entry("Quinoa", 0.75), Map.entry("Black beans", 0.75),
            Map.entry("Walnuts", 0.70), Map.entry("Cottage cheese", 1.0),
            Map.entry("Lemon", 0.90), Map.entry("Flaxseed", 0.65),
            Map.entry("Chia seeds", 0.70), Map.entry("Sweet corn", 0.75),
            Map.entry("Bell pepper", 0.90), Map.entry("Tomato", 0.90),
            Map.entry("Brazil nuts", 0.70)
    );

    private static final Map<String, Integer> GI_MAP = Map.ofEntries(
            Map.entry("White rice", 72), Map.entry("Brown rice", 50),
            Map.entry("Oats", 55), Map.entry("Sardines", 0),
            Map.entry("Garlic", 10), Map.entry("Sweet potato", 44),
            Map.entry("Banana", 51), Map.entry("Blueberries", 25),
            Map.entry("Eggs", 0), Map.entry("Chicken breast", 0),
            Map.entry("Beef mince 10%", 0), Map.entry("Apple", 36),
            Map.entry("Kiwi", 50), Map.entry("Honey", 58),
            Map.entry("Peanut butter", 14), Map.entry("Red lentils", 32),
            Map.entry("Green lentils", 32), Map.entry("Red kidney beans", 24),
            Map.entry("Pearl barley", 25), Map.entry("Whole-wheat spaghetti", 37),
            Map.entry("Peas", 51), Map.entry("Spinach", 15),
            Map.entry("Ginger", 15), Map.entry("Dark chocolate 70%", 23),
            Map.entry("Tahini", 35), Map.entry("Olive oil", 0),
            Map.entry("Salmon", 0), Map.entry("Greek yogurt", 11),
            Map.entry("Broccoli", 15), Map.entry("Avocado", 10),
            Map.entry("Quinoa", 53), Map.entry("Black beans", 30),
            Map.entry("Walnuts", 15), Map.entry("Cottage cheese", 10),
            Map.entry("Lemon", 20), Map.entry("Flaxseed", 35),
            Map.entry("Chia seeds", 1), Map.entry("Sweet corn", 55),
            Map.entry("Bell pepper", 15), Map.entry("Tomato", 15),
            Map.entry("Brazil nuts", 10)
    );

    private static final Map<String, Integer> PREBIOTIC_MAP = Map.ofEntries(
            Map.entry("Garlic", 25), Map.entry("Oats", 20), Map.entry("Banana", 15),
            Map.entry("Peas", 12), Map.entry("Red lentils", 12), Map.entry("Green lentils", 12),
            Map.entry("Red kidney beans", 12), Map.entry("Sweet potato", 8),
            Map.entry("Greek yogurt", 8), Map.entry("Broccoli", 8),
            Map.entry("Avocado", 8), Map.entry("Black beans", 15),
            Map.entry("Walnuts", 5), Map.entry("Flaxseed", 10),
            Map.entry("Chia seeds", 10), Map.entry("Sweet corn", 5),
            Map.entry("Bell pepper", 5), Map.entry("Tomato", 5)
    );

    private static final Map<String, Integer> ANTI_NUTRIENT_MAP = Map.ofEntries(
            Map.entry("Red kidney beans", 15), Map.entry("Red lentils", 8), Map.entry("Green lentils", 8),
            Map.entry("Oats", 5), Map.entry("Spinach", 5),
            Map.entry("Black beans", 10), Map.entry("Walnuts", 5),
            Map.entry("Flaxseed", 10), Map.entry("Chia seeds", 5),
            Map.entry("Broccoli", 5), Map.entry("Brazil nuts", 8)
    );

    private static final Map<String, Double> PHYTO_MAP = Map.ofEntries(
            Map.entry("Garlic", 92.0), Map.entry("Blueberries", 95.0),
            Map.entry("Ginger", 90.0), Map.entry("Dark chocolate 70%", 72.0),
            Map.entry("Olive oil", 85.0), Map.entry("Spinach", 82.0),
            Map.entry("Sardines", 80.0), Map.entry("Kiwi", 75.0),
            Map.entry("Apple", 72.0), Map.entry("Oats", 68.0),
            Map.entry("Peas", 65.0), Map.entry("Sweet potato", 65.0),
            Map.entry("Green lentils", 62.0), Map.entry("Red lentils", 60.0),
            Map.entry("Red kidney beans", 60.0), Map.entry("Banana", 55.0),
            Map.entry("Peanut butter", 55.0), Map.entry("Tahini", 52.0),
            Map.entry("Honey", 50.0), Map.entry("Eggs", 45.0),
            Map.entry("Pearl barley", 42.0), Map.entry("Whole-wheat spaghetti", 38.0),
            Map.entry("Brown rice", 35.0), Map.entry("Chicken breast", 30.0),
            Map.entry("Beef mince 10%", 25.0), Map.entry("White rice", 20.0),
            Map.entry("Broccoli", 95.0), Map.entry("Tomato", 88.0),
            Map.entry("Bell pepper", 82.0), Map.entry("Avocado", 80.0),
            Map.entry("Walnuts", 78.0), Map.entry("Lemon", 75.0),
            Map.entry("Flaxseed", 72.0), Map.entry("Black beans", 72.0),
            Map.entry("Salmon", 70.0), Map.entry("Chia seeds", 65.0),
            Map.entry("Quinoa", 55.0), Map.entry("Sweet corn", 45.0),
            Map.entry("Greek yogurt", 25.0), Map.entry("Cottage cheese", 20.0),
            Map.entry("Brazil nuts", 68.0)
    );

    private static final Map<String, Double> SYNERGY_MAP = Map.ofEntries(
            Map.entry("Garlic", 95.0), Map.entry("Olive oil", 90.0),
            Map.entry("Spinach", 85.0), Map.entry("Oats", 80.0),
            Map.entry("Ginger", 78.0), Map.entry("Eggs", 75.0),
            Map.entry("Kiwi", 75.0), Map.entry("White rice", 72.0),
            Map.entry("Brown rice", 68.0), Map.entry("Sardines", 70.0),
            Map.entry("Blueberries", 65.0), Map.entry("Lemon", 92.0),
            Map.entry("Chicken breast", 55.0), Map.entry("Red lentils", 62.0),
            Map.entry("Green lentils", 62.0), Map.entry("Red kidney beans", 60.0),
            Map.entry("Sweet potato", 58.0), Map.entry("Dark chocolate 70%", 58.0),
            Map.entry("Honey", 60.0), Map.entry("Banana", 55.0),
            Map.entry("Peanut butter", 55.0), Map.entry("Tahini", 52.0),
            Map.entry("Peas", 58.0), Map.entry("Pearl barley", 50.0),
            Map.entry("Whole-wheat spaghetti", 48.0), Map.entry("Beef mince 10%", 45.0),
            Map.entry("Salmon", 75.0), Map.entry("Greek yogurt", 65.0),
            Map.entry("Broccoli", 90.0), Map.entry("Avocado", 82.0),
            Map.entry("Quinoa", 72.0), Map.entry("Black beans", 70.0),
            Map.entry("Walnuts", 70.0), Map.entry("Cottage cheese", 58.0),
            Map.entry("Flaxseed", 62.0), Map.entry("Chia seeds", 65.0),
            Map.entry("Sweet corn", 55.0), Map.entry("Bell pepper", 85.0),
            Map.entry("Tomato", 88.0), Map.entry("Brazil nuts", 50.0),
            Map.entry("Apple", 40.0)
    );

    // Curated educational one-liners — shown on the food card when present. Not persisted;
    // attached to the response at serve time via getStandoutFact().
    private static final Map<String, String> STANDOUT_FACTS = Map.ofEntries(
            Map.entry("Brazil nuts", "Just 2 Brazil nuts (~10g) cover your entire daily selenium — one of the hardest nutrients to find anywhere else in the food supply."),
            Map.entry("Sardines", "Eaten bones-and-all, sardines deliver calcium plus ~3g of EPA+DHA omega-3s per 100g — one of the few foods rich in both."),
            Map.entry("Pearl barley", "One of the richest whole-grain sources of beta-glucan, the soluble fibre shown to lower LDL cholesterol."),
            Map.entry("Oats", "Rich in beta-glucan soluble fibre, which feeds gut bacteria and helps blunt blood-sugar spikes."),
            Map.entry("Spinach", "Among the most nutrient-dense low-calorie foods: 100g covers your vitamin K several times over for only ~23 kcal."),
            Map.entry("Garlic", "Crushing garlic and letting it rest ~10 minutes before cooking activates allicin, its key therapeutic compound."),
            Map.entry("Eggs", "One of the best natural sources of choline, a shortfall nutrient critical for brain and liver function."),
            Map.entry("Kiwi", "Gram for gram, kiwi has more vitamin C than an orange — plus actinidin, an enzyme that aids protein digestion."),
            Map.entry("Flaxseed", "The richest common source of lignans and plant omega-3 (ALA) — grind it first; whole seeds pass through undigested."),
            Map.entry("Walnuts", "The only common nut with meaningful plant omega-3 (ALA), plus polyphenols concentrated in the papery skin."),
            Map.entry("Salmon", "Combines EPA+DHA omega-3s with vitamin D — two of the most under-consumed nutrients — in a single food."),
            Map.entry("Greek yogurt", "Straining removes whey and concentrates the protein to roughly double regular yogurt's, with live cultures included."),
            Map.entry("Broccoli", "A top source of sulforaphane, one of the most-studied phytonutrients — light steaming preserves far more of it than boiling."),
            Map.entry("Chia seeds", "Absorb up to 10× their weight in water, forming a gel that slows digestion and steadies blood sugar."),
            Map.entry("Dark chocolate 70%", "One of the highest polyphenol densities of any food — the higher the cacao %, the more polyphenols and the less sugar."),
            Map.entry("Blueberries", "The anthocyanins in the skins are among the most-studied phytonutrients for brain and vascular health."),
            Map.entry("Sweet potato", "The orange colour is beta-carotene — 100g covers your vitamin A needs; eat it with a little fat to absorb it."),
            Map.entry("Cottage cheese", "One of the highest-casein foods — a slow-digesting protein, which is why lifters traditionally eat it before bed."),
            Map.entry("Avocado", "Its fat helps you absorb fat-soluble vitamins (A, D, E, K) from other foods eaten in the same meal."),
            Map.entry("Lemon", "The vitamin C in a squeeze of lemon can roughly triple non-haem iron absorption from plant foods like lentils and spinach.")
    );

    // Human explanation for every food carrying an ANTI_NUTRIENT_MAP penalty — why the score
    // is lower than the raw nutrients suggest, and what to do about it.
    private static final Map<String, String> ANTI_NUTRIENT_NOTES = Map.ofEntries(
            Map.entry("Red kidney beans", "Contain phytates and lectins — thorough cooking neutralises the lectins, and soaking reduces the phytates."),
            Map.entry("Red lentils", "Phytates bind some of the iron and zinc — soaking, or pairing with vitamin-C foods, improves absorption."),
            Map.entry("Green lentils", "Phytates bind some of the iron and zinc — soaking, or pairing with vitamin-C foods, improves absorption."),
            Map.entry("Oats", "Contain phytic acid, which binds minerals — soaking overnight (as in overnight oats) reduces it."),
            Map.entry("Spinach", "High in oxalates, which bind its own calcium and iron — pair with vitamin-C foods to offset."),
            Map.entry("Black beans", "Phytates and lectins — well cooked, most are neutralised, and the fibre benefit far outweighs the rest."),
            Map.entry("Walnuts", "Contain some phytic acid — light toasting or soaking reduces it."),
            Map.entry("Brazil nuts", "Contain some phytic acid — light toasting or soaking reduces it."),
            Map.entry("Flaxseed", "Contains phytates and cyanogenic glycosides — harmless at normal intakes of 1–2 tablespoons a day."),
            Map.entry("Chia seeds", "Contain some phytic acid — negligible at typical serving sizes."),
            Map.entry("Broccoli", "Raw broccoli contains goitrogens that can interfere with iodine uptake — cooking largely deactivates them.")
    );

    // Short chip label for the watch badge — one per food in ANTI_NUTRIENT_NOTES. Unlike
    // strength badges (nutrient keys, formatted client-side), these are literal display text.
    private static final Map<String, String> ANTI_NUTRIENT_BADGES = Map.ofEntries(
            Map.entry("Red kidney beans", "Lectins"),
            Map.entry("Red lentils", "Phytates"),
            Map.entry("Green lentils", "Phytates"),
            Map.entry("Oats", "Phytates"),
            Map.entry("Spinach", "Oxalates"),
            Map.entry("Black beans", "Phytates"),
            Map.entry("Walnuts", "Phytates"),
            Map.entry("Brazil nuts", "Phytates"),
            Map.entry("Flaxseed", "Phytates"),
            Map.entry("Chia seeds", "Phytates"),
            Map.entry("Broccoli", "Goitrogens (raw)")
    );

    // Foods with a genuine upper limit — a different axis from anti-nutrients (which you can
    // reduce by soaking/cooking). These are "don't eat more than X" caps, shown as a distinct
    // cap badge with the reason in the popover.
    private static final Map<String, String> CAP_BADGES = Map.ofEntries(
            Map.entry("Brazil nuts", "Max 2/day"),
            Map.entry("Walnuts", "Max 15–20g/day")
    );
    private static final Map<String, String> CAP_NOTES = Map.ofEntries(
            Map.entry("Brazil nuts", "Selenium is so concentrated that more than ~4 nuts a day can exceed the safe upper limit — treat it like a supplement, not a snack. Two nuts already cover your full daily selenium."),
            Map.entry("Walnuts", "Genuinely good fats, but a high omega-6 load in larger amounts — a 15–20g topping is the sweet spot, not a daily handful.")
    );

    // A nutrient earns a strength badge when 100g covers at least half its RDA.
    private static final double BADGE_MIN_PCT_RDA = 50.0;
    private static final int BADGE_STRENGTH_LIMIT = 3;

    // Overall score: top four stats weighted 50%, 30%, 15%, 5% (lowest stat ignored)
    // Best stat counts most and the worst is dropped, so a food isn't marked down for something it isn't for.
    private static final double[] OVERALL_STAT_WEIGHTS = {0.50, 0.30, 0.15, 0.05};

    // --- Two-axis "fuel" model (gastric-emptying, not glycaemic index) ---
    // The energy/timing score places a food in a 2D space and measures how close it sits to
    // each context's ideal quadrant (from the owner's Carbmaxxing Fuel Identification matrix):
    //   stomachSpeed — how fast it leaves the stomach. Fat, fibre and protein are "brakes"
    //                  that slow emptying; 1 = empties fast (rice, banana), 0 = sits heavy.
    //   bloodSpeed   — how fast glucose hits the blood, proxied by GI. 1 = spike, 0 = trickle.
    // "The Coma" (slow stomach + fast blood, e.g. pizza) sits far from every good target and
    // scores low everywhere — which is the whole point.
    private static final double BRAKE_K = 18.0;       // brake load that fully slows emptying
    private static final double MAX_FUEL_DIST = Math.sqrt(2.0);  // corner-to-corner in the unit square

    // Target {stomachSpeed, bloodSpeed} per context.
    private static final Map<TimingContext, double[]> FUEL_TARGETS = new EnumMap<>(Map.of(
            TimingContext.MORNING,      new double[]{0.25, 0.30},  // Diesel — slow, sustained
            TimingContext.PRE_WORKOUT,  new double[]{1.00, 0.75},  // Rocket fuel — fast + fast
            TimingContext.POST_WORKOUT, new double[]{0.75, 0.60},  // fast-ish carbs to refill glycogen
            TimingContext.EVENING,      new double[]{0.70, 0.20},  // soft on the stomach, low-GI for sleep
            TimingContext.NEUTRAL,      new double[]{0.50, 0.35}   // balanced, no workout skew
    ));

    // One row per tracked nutrient: JSON key, daily target (RDA), rarity weight, and how to read it
    // from the USDA data. Keeping them in one row means they can't fall out of step, unlike the
    // separate arrays this replaced. Order matters only for the order of the JSON output.
    // Rarity is a bonus, not a redistribution: shortfall nutrients (under-consumed in typical diets
    // or concentrated in few foods) count extra, while abundant nutrients keep full baseline weight,
    // so a food rich in easy-to-get nutrients is never marked down for it.
    // Tiers: 1.5 rare/shortfall, 1.25 under-consumed, 1.0 everything else.
    private enum Nutrient {
        VITAMIN_A("vitaminA", 900, 1.0, NutrientData::vitaminA),
        VITAMIN_C("vitaminC", 90, 1.0, NutrientData::vitaminC),
        VITAMIN_D("vitaminD", 20, 1.5, NutrientData::vitaminD),
        VITAMIN_E("vitaminE", 15, 1.25, NutrientData::vitaminE),
        VITAMIN_K("vitaminK", 120, 1.25, NutrientData::vitaminK),
        VITAMIN_B1("vitaminB1", 1.2, 1.0, NutrientData::vitaminB1),
        VITAMIN_B2("vitaminB2", 1.3, 1.0, NutrientData::vitaminB2),
        VITAMIN_B3("vitaminB3", 16, 1.0, NutrientData::vitaminB3),
        VITAMIN_B6("vitaminB6", 1.7, 1.0, NutrientData::vitaminB6),
        VITAMIN_B12("vitaminB12", 2.4, 1.0, NutrientData::vitaminB12),
        FOLATE("folate", 400, 1.25, NutrientData::folate),
        CALCIUM("calcium", 1000, 1.25, NutrientData::calcium),
        IRON("iron", 18, 1.25, NutrientData::iron),
        MAGNESIUM("magnesium", 420, 1.25, NutrientData::magnesium),
        PHOSPHORUS("phosphorus", 700, 1.0, NutrientData::phosphorus),
        POTASSIUM("potassium", 3500, 1.5, NutrientData::potassium),
        ZINC("zinc", 11, 1.25, NutrientData::zinc),
        SELENIUM("selenium", 55, 1.25, NutrientData::selenium),
        COPPER("copper", 0.9, 1.0, NutrientData::copper),
        CHOLINE("choline", 550, 1.5, NutrientData::choline),
        PANTOTHENIC_ACID("pantothenicAcid", 5, 1.0, NutrientData::pantothenicAcid),
        BIOTIN("biotin", 30, 1.0, NutrientData::biotin),
        MANGANESE("manganese", 2.3, 1.0, NutrientData::manganese),
        IODINE("iodine", 150, 1.5, NutrientData::iodine),
        EPA("epa", 0.5, 1.5, NutrientData::epa),
        DHA("dha", 0.5, 1.5, NutrientData::dha);

        final String key;
        final double rda;
        final double rarity;
        final ToDoubleFunction<NutrientData> reader;

        Nutrient(String key, double rda, double rarity, ToDoubleFunction<NutrientData> reader) {
            this.key = key;
            this.rda = rda;
            this.rarity = rarity;
            this.reader = reader;
        }
    }

    private static final Nutrient[] NUTRIENTS = Nutrient.values();

    private static final double TOTAL_RARITY_WEIGHT = Arrays.stream(NUTRIENTS).mapToDouble(n -> n.rarity).sum();

    // Curve steepness, a tuning number: a food averaging 10% adequacy across all 26 nutrients per
    // 100 kcal scores ~63. The old hard cap put 23 of 41 foods at exactly 100; this gives spinach
    // ~93, a median near 35, and no food at 100.
    private static final double MICRO_CURVE_K = 0.10;

    private static final Set<String> RARE_NUTRIENTS = Arrays.stream(NUTRIENTS)
            .filter(n -> n.rarity >= 1.25)
            .map(n -> n.key)
            .collect(Collectors.toUnmodifiableSet());

    // Nutrients the body stores, where the weekly average matters more than any single day:
    //   - fat-soluble vitamins A/D/E/K (adipose and liver stores)
    //   - B12 (liver stores last months)
    //   - EPA/DHA (membrane incorporation — "oily fish twice a week" is the standard advice)
    //   - calcium (bone), iron/copper (liver ferritin), zinc/selenium (tissue stores)
    // Everything else is a daily target: the water-soluble vitamins (B-complex, C) and the
    // "sweat tax" electrolytes magnesium and potassium, which are actively flushed during
    // training and hold almost no reserve.
    private static final Set<String> WEEKLY_NUTRIENTS = Set.of(
            "vitaminA", "vitaminD", "vitaminE", "vitaminK", "vitaminB12", "epa", "dha",
            "calcium", "iron", "copper", "zinc", "selenium");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- Public entry point ---

    // Score from live USDA data, or empty if USDA has no data or can't be reached.
    // This waits on the USDA API (blocking, 10s timeout). Scores get saved, so it's once per food,
    // and DataSeeder fills in any missing ones in the background at startup.
    public Optional<NutritionScore> calculateFromUsda(Food food) {
        NutrientData data = nutrientDataService.fetchNutrientData(food.getName());
        return data == null ? Optional.empty() : Optional.of(calculateFromRealData(food, data));
    }

    // Always returns a score: real data if there is any, otherwise the fallback below.
    // Anything that saves scores should go through NutritionScoreService, which never saves a fallback.
    public NutritionScore calculateScores(Food food) {
        return calculateFromUsda(food).orElseGet(() -> calculateFallback(food));
    }

    public boolean hasFallback(Food food) {
        return FALLBACK_FOODS.contains(food.getName());
    }

    // --- Core scoring ---

    // No network or DB in here, so the same nutrient data always gives the same score.
    // That makes it easy to test: ScoringServiceTest mocks the USDA client, and ScoringGoldenTest
    // compares every output field for all foods, so a refactor can't change scores unnoticed.
    private NutritionScore calculateFromRealData(Food food, NutrientData data) {
        NutritionScore score = new NutritionScore();
        score.setFood(food);

        String name = food.getName();

        // Bioavailability
        double bioavailability = BIOAVAILABILITY_MAP.getOrDefault(name, 0.80);
        score.setKcalPer100g(data.energyKcal100g());

        // 1. Protein quality
        double pdcaas = PDCAAS_MAP.getOrDefault(name, 0.70);
        double completeness = COMPLETENESS_MAP.getOrDefault(name, 0.70);
        double volumeScore = Math.min(data.proteins100g() / 22.0, 1.0) * 50;
        double qualityScore = pdcaas * completeness * 50;
        double proteinQuality = volumeScore + qualityScore;

        // 2. Micronutrient density — RDA coverage per 100 kcal across all tracked nutrients.
        // Scoring per 100 kcal (not per 100g) so calorie-dense foods like peanut butter can't game
        // the formula by volume. The weighted average coverage is then passed through a saturating
        // curve (see MICRO_CURVE_K) so even spinach stays below 100 and foods keep their rank order.
        double kcal = data.energyKcal100g();
        if (kcal <= 0) {
            log.warn("energyKcal missing for '{}' — using 100 kcal fallback for micronutrient density", name);
            kcal = 100.0;
        }
        double perKcalScale = 100.0 / kcal;
        // Coverage stays capped at 1.0 per nutrient inside the score (mega-doses shouldn't
        // multiply it); the uncapped per-100g percentages are surfaced in microBreakdown instead.
        double weightedCoverageSum = 0;
        double[] pctRdaPer100g = new double[NUTRIENTS.length];
        for (int i = 0; i < NUTRIENTS.length; i++) {
            double value = NUTRIENTS[i].reader.applyAsDouble(data);
            double coverage = Math.min(value * perKcalScale / NUTRIENTS[i].rda, 1.0);
            weightedCoverageSum += coverage * NUTRIENTS[i].rarity;
            pctRdaPer100g[i] = value / NUTRIENTS[i].rda * 100.0;
        }

        // Average adequacy per 100 kcal, 0..1 (1 would mean every nutrient fully covered).
        double meanCoverage = weightedCoverageSum / TOTAL_RARITY_WEIGHT;
        double micronutrientDensity = 100 * (1 - Math.exp(-meanCoverage * bioavailability / MICRO_CURVE_K));

        // 3. Energy profile (NEUTRAL default)
        double energyProfile = calculateEnergyProfile(data, name, TimingContext.NEUTRAL);

        // 4. Gut health
        double fibreScoreGut = Math.min(data.fiber100g() / 10.0, 1.0) * 60;
        int prebioticBonus = PREBIOTIC_MAP.getOrDefault(name, 0);
        int antiNutrientPenalty = ANTI_NUTRIENT_MAP.getOrDefault(name, 0);
        // EPA+DHA support gut microbiome diversity; capped at 20 points
        double omega3Bonus = Math.min((data.epa() + data.dha()) * 7.0, 20.0);
        double gutHealth = Math.min(Math.max(fibreScoreGut + prebioticBonus + omega3Bonus - antiNutrientPenalty, 0), 100);

        // 5. Phytonutrients
        double phytonutrients = PHYTO_MAP.getOrDefault(name, 20.0);

        // Synergy potential
        double synergyPotential = SYNERGY_MAP.getOrDefault(name, 40.0);

        // Timing scores
        Map<String, Double> timingScores = calculateTimingScores(food, data,
                proteinQuality, micronutrientDensity, gutHealth, phytonutrients);

        // Energy breakdown values (NEUTRAL context)
        int gi = GI_MAP.getOrDefault(name, 50);
        double unsaturatedRatio = data.fat100g() > 0
                ? (data.monounsaturatedFat100g() + data.polyunsaturatedFat100g()) / data.fat100g()
                : 0.5;

        // Set all fields
        score.setProteinQuality(round(proteinQuality));
        score.setMicronutrientDensity(round(micronutrientDensity));
        score.setEnergyProfile(round(energyProfile));
        score.setGutHealth(round(gutHealth));
        score.setPhytonutrients(round(phytonutrients));
        score.setBioavailabilityModifier(bioavailability);
        applyOverallScore(score);
        score.setSynergyPotential(synergyPotential);
        score.setEnergyProfileNeutral(round(energyProfile));

        // Breakdowns are JSON strings in TEXT columns. Easy to change, but types.ts has to be kept
        // in step by hand, and nothing checks it, so the two can drift (it did once).
        score.setProteinBreakdown(String.format(Locale.ROOT,
                "{\"rawProteinG\":%.2f,\"pdcaas\":%.2f,\"completenessFactor\":%.2f,\"bioavailability\":%.2f}",
                data.proteins100g(), pdcaas, completeness, bioavailability));
        score.setEnergyBreakdown(String.format(Locale.ROOT,
                "{\"fibreG\":%.2f,\"gi\":%d,\"sugarsG\":%.2f,\"unsaturatedRatio\":%.2f,\"stomachSpeed\":%.2f,\"bloodSpeed\":%.2f}",
                data.fiber100g(), gi, data.sugars100g(), unsaturatedRatio,
                stomachSpeed(data), bloodSpeed(data, name)));
        score.setGutBreakdown(String.format(Locale.ROOT,
                "{\"fibreG\":%.2f,\"prebioticBonus\":%d,\"antiNutrientPenalty\":%d,\"omega3Bonus\":%.1f}",
                data.fiber100g(), prebioticBonus, antiNutrientPenalty, omega3Bonus));
        score.setMicroBreakdown(buildMicroBreakdown(pctRdaPer100g));

        // Timing scores as JSON string
        StringBuilder tsJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : timingScores.entrySet()) {
            if (!first) tsJson.append(",");
            tsJson.append(String.format(Locale.ROOT, "\"%s\":%.2f", e.getKey(), e.getValue()));
            first = false;
        }
        tsJson.append("}");
        score.setTimingScores(tsJson.toString());

        return score;
    }

    // --- Energy profile with timing context ---

    // Scores how close the food is to the ideal for this context, on two axes:
    // how fast it leaves the stomach and how fast it hits the blood.
    private double calculateEnergyProfile(NutrientData data, String foodName, TimingContext timingContext) {
        double stomach = stomachSpeed(data);
        double blood = bloodSpeed(data, foodName);

        double[] target = FUEL_TARGETS.get(timingContext);
        double dist = Math.hypot(stomach - target[0], blood - target[1]);
        double score = 100.0 * (1.0 - dist / MAX_FUEL_DIST);

        // Health guard: outside the workout windows, fast blood glucose from unbuffered added
        // sugar should NOT read as good sustained fuel — otherwise treats like honey score well
        // at breakfast/anytime. Pre/post-workout deliberately keep the rocket-fuel reward.
        if (timingContext != TimingContext.PRE_WORKOUT && timingContext != TimingContext.POST_WORKOUT) {
            double sugars = data.sugars100g();
            boolean fibreBuffered = sugars > 0 && data.fiber100g() / sugars >= 0.15;
            if (sugars > 0 && !fibreBuffered) {
                score -= Math.min(sugars / 25.0, 1.0) * 15.0;
            }
        }

        return clamp(score, 0, 100);
    }

    // How fast the food leaves the stomach. Fat is the strongest brake, then fibre, then
    // protein; a food with little of any (white rice, banana, honey) empties fast → ~1.
    private double stomachSpeed(NutrientData data) {
        double brakeLoad = data.fat100g() * 3.0 + data.fiber100g() * 2.0 + data.proteins100g();
        return clamp(1.0 - brakeLoad / BRAKE_K, 0, 1);
    }

    // How fast glucose reaches the blood, proxied by glycaemic index.
    private double bloodSpeed(NutrientData data, String foodName) {
        return clamp(GI_MAP.getOrDefault(foodName, 50) / 100.0, 0, 1);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    // --- Timing scores for all 5 contexts ---

    // Same five stats weighted differently per context, with energy recomputed each time.
    // That's why meals average these per-food scores instead of re-weighting averaged stats.
    private Map<String, Double> calculateTimingScores(Food food, NutrientData data,
            double protein, double micro, double gut, double phyto) {

        String name = food.getName();
        Map<String, Double> result = new LinkedHashMap<>();

        // Order must match timingWeights rows below
        TimingContext[] contexts = {TimingContext.MORNING, TimingContext.PRE_WORKOUT,
                TimingContext.POST_WORKOUT, TimingContext.EVENING, TimingContext.NEUTRAL};
        // Columns: {protein, micro, energy, gut, phyto}
        double[][] timingWeights = {
                {0.25, 0.30, 0.20, 0.15, 0.10},  // MORNING
                {0.10, 0.05, 0.65, 0.10, 0.10},  // PRE_WORKOUT
                {0.55, 0.15, 0.20, 0.05, 0.05},  // POST_WORKOUT
                {0.10, 0.20, 0.05, 0.45, 0.20},  // EVENING
                {0.25, 0.20, 0.25, 0.15, 0.15}   // NEUTRAL
        };

        for (int i = 0; i < contexts.length; i++) {
            double energy = calculateEnergyProfile(data, name, contexts[i]);
            double[] w = timingWeights[i];
            double ts = protein * w[0] + micro * w[1] + energy * w[2] + gut * w[3] + phyto * w[4];
            result.put(contexts[i].name(), round(ts));
        }

        return result;
    }

    // --- Insight lookups (serve-time, not persisted) ---

    public String getStandoutFact(String foodName) {
        return STANDOUT_FACTS.get(foodName);
    }

    public String getPenaltyNote(String foodName) {
        return ANTI_NUTRIENT_NOTES.get(foodName);
    }

    public static boolean isRareNutrient(String nutrientName) {
        return RARE_NUTRIENTS.contains(nutrientName);
    }

    public static String nutrientCadence(String nutrientName) {
        return WEEKLY_NUTRIENTS.contains(nutrientName) ? "WEEKLY" : "DAILY";
    }

    private static final String RARE_BADGE_DETAIL =
            "A shortfall nutrient — hard to find in most diets, so foods rich in it are worth seeking out.";

    // Derived at serve time, never persisted: up to three strength badges from the persisted
    // coverage vector (rare nutrients first — they differentiate foods — then by coverage),
    // plus a watch chip for foods carrying an anti-nutrient note. A null or fallback score
    // (empty coverages) yields no strength badges rather than an error.
    public List<Badge> deriveBadges(NutritionScore score, String foodName) {
        List<Badge> badges = new ArrayList<>();
        if (score != null) {
            parseCoverages(score).entrySet().stream()
                    .filter(e -> e.getValue() >= BADGE_MIN_PCT_RDA)
                    .sorted(Comparator
                            .comparing((Map.Entry<String, Double> e) -> !isRareNutrient(e.getKey()))
                            .thenComparing(Map.Entry::getValue, Comparator.reverseOrder()))
                    .limit(BADGE_STRENGTH_LIMIT)
                    .forEach(e -> badges.add(isRareNutrient(e.getKey())
                            ? new Badge(e.getKey(), "rare", RARE_BADGE_DETAIL)
                            : new Badge(e.getKey(), "strength", null)));
        }
        String watch = ANTI_NUTRIENT_BADGES.get(foodName);
        if (watch != null) {
            badges.add(new Badge(watch, "watch", ANTI_NUTRIENT_NOTES.get(foodName)));
        }
        String cap = CAP_BADGES.get(foodName);
        if (cap != null) {
            badges.add(new Badge(cap, "cap", CAP_NOTES.get(foodName)));
        }
        return badges;
    }

    // Parses the persisted timingScores JSON (context name -> score). Empty map when the
    // score has no timing data (fallback-scored rows persist the "{}" sentinel).
    public Map<String, Double> parseTimingScores(NutritionScore score) {
        if (score.getTimingScores() == null) return Map.of();
        try {
            JsonNode node = MAPPER.readTree(score.getTimingScores());
            Map<String, Double> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asDouble()));
            return result;
        } catch (Exception e) {
            log.warn("Could not parse timingScores for score {}: {}", score.getId(), e.getMessage());
            return Map.of();
        }
    }

    // Parses the coverages map back out of a persisted microBreakdown (%RDA per 100g).
    // Lives here because buildMicroBreakdown() below owns the JSON shape.
    public Map<String, Double> parseCoverages(NutritionScore score) {
        if (score.getMicroBreakdown() == null) return Map.of();
        try {
            JsonNode coverages = MAPPER.readTree(score.getMicroBreakdown()).path("coverages");
            Map<String, Double> result = new LinkedHashMap<>();
            coverages.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asDouble()));
            return result;
        } catch (Exception e) {
            log.warn("Could not parse microBreakdown for score {}: {}", score.getId(), e.getMessage());
            return Map.of();
        }
    }

    // --- Utilities ---

    // Built with Jackson rather than String.format: the coverages map is 26 entries, and
    // FoodService.compare() parses this JSON back — hand-rolled formatting isn't worth the risk.
    private String buildMicroBreakdown(double[] pctRdaPer100g) {
        Integer[] indices = new Integer[pctRdaPer100g.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(pctRdaPer100g[b], pctRdaPer100g[a]));

        ObjectNode json = MAPPER.createObjectNode();
        json.put("scoreCurve", "saturating");  // canary for DataSeeder Fix 12
        ArrayNode top = json.putArray("topNutrients");
        for (int rank = 0; rank < 3 && rank < indices.length; rank++) {
            int i = indices[rank];
            if (pctRdaPer100g[i] <= 0) break;
            ObjectNode entry = top.addObject();
            entry.put("name", NUTRIENTS[i].key);
            entry.put("pctRda", round1(pctRdaPer100g[i]));
            entry.put("rare", isRareNutrient(NUTRIENTS[i].key));
        }
        ObjectNode coverages = json.putObject("coverages");
        for (int i = 0; i < pctRdaPer100g.length; i++) {
            coverages.put(NUTRIENTS[i].key, round1(pctRdaPer100g[i]));
        }
        return json.toString();
    }

    private double calculateOverallFromStats(double protein, double micro, double energy,
                                             double gut, double phyto) {
        double[] stats = {protein, micro, energy, gut, phyto};
        Integer[] indices = {0, 1, 2, 3, 4};
        Arrays.sort(indices, (a, b) -> {
            int byScore = Double.compare(stats[b], stats[a]);
            return byScore != 0 ? byScore : Integer.compare(a, b);
        });
        double overall = 0;
        for (int i = 0; i < 4; i++) {
            overall += stats[indices[i]] * OVERALL_STAT_WEIGHTS[i];
        }
        return overall;
    }

    private void applyOverallScore(NutritionScore score) {
        score.setOverallScore(round(calculateOverallFromStats(
                score.getProteinQuality(),
                score.getMicronutrientDensity(),
                score.getEnergyProfile(),
                score.getGutHealth(),
                score.getPhytonutrients())));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // --- Fallback ---

    // Only these three foods have real hard-coded fallback scores (keep in step with the cases below).
    // Everything else falls back to zeros, so NutritionScoreService serves a fallback while USDA is
    // down but never saves it, and refuses foods that have none.
    private static final Set<String> FALLBACK_FOODS = Set.of("Sardines", "Oats", "Garlic");

    NutritionScore calculateFallback(Food food) {
        NutritionScore score = new NutritionScore();
        score.setFood(food);

        switch (food.getName()) {
            case "Sardines" -> {
                score.setProteinQuality(88.0);
                score.setMicronutrientDensity(85.0);
                score.setEnergyProfile(60.0);
                score.setGutHealth(20.0);
                score.setPhytonutrients(82.0);
                score.setBioavailabilityModifier(0.95);
            }
            case "Oats" -> {
                score.setProteinQuality(45.0);
                score.setMicronutrientDensity(52.0);
                score.setEnergyProfile(78.0);
                score.setGutHealth(85.0);
                score.setPhytonutrients(40.0);
                score.setBioavailabilityModifier(0.75);
            }
            case "Garlic" -> {
                score.setProteinQuality(10.0);
                score.setMicronutrientDensity(48.0);
                score.setEnergyProfile(15.0);
                score.setGutHealth(72.0);
                score.setPhytonutrients(88.0);
                score.setBioavailabilityModifier(0.90);
            }
            default -> {
                score.setProteinQuality(0.0);
                score.setMicronutrientDensity(0.0);
                score.setEnergyProfile(0.0);
                score.setGutHealth(0.0);
                score.setPhytonutrients(0.0);
                score.setBioavailabilityModifier(1.0);
            }
        }

        applyOverallScore(score);

        // Carry the topNutrients sentinel so DataSeeder Fix 6 doesn't delete fallback scores
        // on every startup (which would re-hit the USDA API for foods it already failed on).
        score.setMicroBreakdown("{\"topNutrients\":[],\"coverages\":{}}");
        // Same idea for Fix 9: "{}" marks "no timing data" without matching the IS NULL canary.
        score.setTimingScores("{}");
        // And for Fix 11: carry the stomachSpeed key so the energy-model canary is a no-op here.
        score.setEnergyBreakdown("{\"stomachSpeed\":0.00,\"bloodSpeed\":0.00}");

        return score;
    }
}
