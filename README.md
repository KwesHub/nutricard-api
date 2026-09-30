# NutriCard

[![CI](https://github.com/KwesHub/nutricard-api/actions/workflows/ci.yml/badge.svg)](https://github.com/KwesHub/nutricard-api/actions/workflows/ci.yml)

A nutrition API that treats food like a football card.

Every food gets a stat card: Protein Quality, Micronutrient Density, Energy Profile, Gut Health, and Phytonutrients. No food scores zero across the board, and none scores 100 across it either. That's the point.

The React frontend lives in [nutricard-frontend](https://github.com/KwesHub/nutricard-frontend).

<!-- Add the live demo links here once confirmed: frontend (Vercel) and API (Railway). -->

---

## Why I built this

Scroll through Instagram for five minutes and you'll find someone telling you oats are poison, carbs are the enemy, or that raw meat is the ancestral diet we've been missing. These takes get millions of views because controversy travels. The problem is that someone with a chronic illness might see that content, take it seriously, and end up worse off.

I got tired of watching foods get demonised based on a single compound or a clip taken out of context. Oats have phytic acid. Yes. That doesn't make oats bad. It means you cook them, or pair them with vitamin C, and the absorption problem largely disappears. Spinach has oxalates. Liver has a lot of vitamin A. Context matters. Dose matters. Combination matters.

The same problem exists on the other side too. Chia seeds and flax seeds get promoted as omega-3 sources, and technically they are, but the omega-3 in them is ALA, and the conversion rate from ALA to the EPA and DHA your body actually uses is around 5-10% at best. That is not an argument against chia seeds. They are genuinely good for fibre and phytonutrients. It is an argument for knowing what a food actually does, rather than what someone on the internet says it does.

Most people don't need a drastic diet overhaul. They need better information. Someone eating chicken and chips isn't doing something terrible, they're one air fryer and a portion of vegetables away from a genuinely solid meal. That's the gap I wanted to close.

NutriCard is built around one idea: no food is inherently good or bad. Different foods have different strengths, and the goal is variety, balance, and understanding what you're actually eating.

---

## What it does

### Food cards

There are 41 foods. Each is scored from 0 to 100 on five stats, using per-100g data from the USDA FoodData Central database.

- **Protein Quality:** half the score is how much protein there is (22g per 100g earns the full half), the other half is how good it is, using PDCAAS and how complete the amino acids are.
- **Micronutrient Density:** how much of the daily target 26 vitamins and minerals cover per 100 kcal. Nutrients that are hard to get from a typical diet, such as vitamin D, potassium, choline and EPA/DHA, count for extra. Measuring per calorie stops peanut butter winning just because it is dense.
- **Energy Profile:** how well the food suits a moment, judged on two things: how fast it leaves the stomach (fat, fibre and protein slow it down) and how fast it raises blood sugar (glycaemic index).
- **Gut Health:** fibre, plus a bonus for prebiotic foods and for the omega-3s in oily fish, minus a penalty for anti-nutrients.
- **Phytonutrients:** a value I set by hand for each food.

The overall score takes the best four stats, weighted 50/30/15/5, and ignores the lowest. A food is not marked down for something it was never meant to do. Sardines have no fibre, and that is fine.

Each card also carries a standout fact, a badge for any nutrient where 100g covers at least half the daily target, and "watch" badges for things like phytates, oxalates and lectins, with a note on how to reduce them. A few foods have a "cap" badge, for example Brazil nuts (max 2 a day, because of the selenium).

### Food roles

Every food has one of five roles:

- **Eat daily:** oats, eggs, spinach, lentils, sweet potato
- **2-3 times a week:** sardines, salmon, beef mince
- **Small boost:** nuts, seeds, berries, peanut butter
- **Flavour staple:** garlic, lemon, olive oil, tahini
- **Treat:** honey, dark chocolate

Flavour staples and treats have no timing grades, because nobody eats garlic for breakfast.

### Timing

Foods and meals are scored for five contexts: morning, pre-workout, post-workout, evening and anytime. Each context has its own ideal on the two energy axes and its own weighting of the five stats. A low-fat, high-GI food like white rice grades well before a workout. A high-fibre food like oats does not.

### Meals

`POST /meals` takes a list of foods with grams and a timing context.

- The stats are averaged by weight, so 150g of sardines counts for more than 10g of garlic. The overall score is the weighted average of each food's score for that context.
- Some combinations are flagged: oily fish with garlic or onion, oats or spinach with a vitamin C food, tomato with a fat, and a high-protein food with a high-fibre one.
- Any nutrient the whole meal covers at under 10% of the daily target is listed as a gap. Storable nutrients (for example fat-soluble vitamins, B12, omega-3, calcium, iron and zinc) are marked weekly, because their weekly average matters more than any one day. Up to three foods that would fill the gaps are suggested.

### Compare

`GET /foods/compare?a=1&b=2` returns the winner on each stat, the nutrients one food has and the other barely does, and the nutrients both have where one has at least 1.5 times as much.

### How far to trust the numbers

Nutrient amounts come from USDA. Several inputs do not: amino-acid completeness, bioavailability, glycaemic index, prebiotic and anti-nutrient values, the phytonutrient score and the synergy score are values I chose per food. The micronutrient formula is divided by a constant (1.31) picked so that peanut butter scores about 58.8. The scores rank foods against each other. They are not clinical measurements.

---

## API

```
GET  /foods                           List all foods with their badges (optional ?search=)
GET  /foods/{id}/card                 A food's full card (scores, breakdowns, timing grades, insights)
GET  /foods/compare?a={id}&b={id}     Head-to-head comparison of two foods
POST /meals                           Score a meal built from foods and grams
GET  /meals/{id}/card                 A saved meal's card
```

Interactive docs are at `/swagger-ui.html`, and the OpenAPI JSON is at `/api-docs`.

**Example: create a meal** (on a freshly seeded database, IDs 1 to 3 are Sardines, Oats and Garlic)

```bash
curl -X POST http://localhost:8080/meals \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Post Workout Bowl",
    "timingContext": "POST_WORKOUT",
    "foods": [
      {"foodId": 1, "quantityG": 150},
      {"foodId": 2, "quantityG": 80},
      {"foodId": 3, "quantityG": 10}
    ]
  }'
```

An invalid meal gets a 400 that names the field, for example `{"error":"Invalid request","fields":{"foods":"must not be empty"}}`. A food with no saved score, when USDA cannot be reached, gets a 503.

---

## Tech stack

Java 21, Spring Boot 3.3, Spring Data JPA (Hibernate), PostgreSQL, Spring WebClient for the USDA API, springdoc for Swagger, Maven and Docker. Tests use JUnit 5, Mockito and an in-memory H2 database.

## How it's put together

Controllers only route requests. `FoodService` and `MealService` hold the request logic, `ScoringService` holds the scoring rules, and `NutrientDataService` talks to USDA. `NutritionScoreService` is the single place that returns a food's saved score or computes and saves it.

Scores are computed once, from USDA data, and saved. A score that fails to save because another thread saved the same food first takes the other thread's row.

On startup, `DataSeeder` runs a set of SQL migrations that drop scores made by older scoring logic, seeds any missing foods, and rescores in a background thread.

---

## Running it locally

You need Java 21, PostgreSQL and a free USDA API key ([sign up here](https://fdc.nal.usda.gov/api-key-signup.html)).

```bash
git clone https://github.com/KwesHub/nutricard-api.git
cd nutricard-api
createdb nutricard_db
export USDA_API_KEY=your_key_here
./mvnw spring-boot:run
```

The app reads its configuration from environment variables:

| Variable | Default |
|---|---|
| `PGHOST`, `PGPORT`, `PGDATABASE` | `localhost`, `5432`, `nutricard_db` |
| `PGUSER`, `PGPASSWORD` | `eka` and empty. Set `PGUSER` to your own database user |
| `USDA_API_KEY` | none |
| `PORT` | `8080` |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:5174` |

On an empty database the first start seeds 41 foods and scores each one by calling USDA, so it takes a little while.

## Tests

```bash
./mvnw test
```

The 59 tests need no database and no USDA key. They cover the scoring rules, meal scoring, request validation, the USDA-outage behaviour, a race between two threads saving the same score, and a check that every food-name key in the scoring tables matches a real food. `ScoringGoldenTest` compares every output field for every food against a saved file, so a refactor cannot change a score without a test failing. GitHub Actions runs them on every push.

## Deployment

The Dockerfile builds the jar in one stage and runs it on a JRE in the next. The API runs on Railway with a managed Postgres, and the frontend runs on Vercel. `CORS_ORIGINS` has to include the frontend's URL.

---

## Known limitations

- **USDA dependency.** The first request for a food needs USDA to be reachable. If it is down and the food has no saved score, the API returns 503. Sardines, Oats and Garlic fall back to built-in values instead. The raw USDA data is not stored, so rescoring needs the API again.
- **Some USDA entries are dry weights.** I corrected green lentils to cooked values. White rice and red lentils probably have the same problem.
- **Schema changes.** Hibernate creates the tables (`ddl-auto=update`), and scoring changes go out as SQL migrations at startup. A tool such as Flyway would be safer.
- **Validation and security.** Only `POST /meals` validates its input. There is no authentication and no rate limiting.

## What's next

- A fat-soluble vitamin modifier (vitamins A, D, E and K absorb better with fat in the meal)
- Explanations for terms like PDCAAS and glycaemic index in the UI
- Fibre types and plant variety in meal scoring
- Calorie goals (cut, maintain, bulk) on the calorie calculator
- Flavour profiles (salt, acid, fat, heat, umami) and meal suggestions that make culinary sense
- A day planner and dietary profiles (vegetarian, vegan and so on)
- More foods, such as chickpeas, tofu, kefir and almonds

---

## A note on the data

Nutritional data comes from the USDA FoodData Central SR Legacy dataset. This is a US government database, which means branded UK supermarket products are not covered. Nutrient profiles for generic whole foods (oats, sardines, garlic) are accurate regardless of geography. Branded product support and UK-specific data (McCance and Widdowson) is on the roadmap.
