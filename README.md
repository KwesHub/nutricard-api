# NutriCard

A nutrition API that treats food like a football card.

Every food gets a stat card: Protein Quality, Micronutrient Density, Energy Profile, Gut Health, and Phytonutrients. No food scores zero across the board. No food scores 100 either. That's the point.

---

## Why I built this

Scroll through Instagram for five minutes and you'll find someone telling you oats are poison, carbs are the enemy, or that raw meat is the ancestral diet we've been missing. These takes get millions of views because controversy travels. The problem is that someone with a chronic illness might see that content, take it seriously, and end up worse off.

I got tired of watching foods get demonised based on a single compound or a clip taken out of context. Oats have phytic acid. Yes. That doesn't make oats bad. It means you cook them, or pair them with vitamin C, and the absorption problem largely disappears. Spinach has oxalates. Liver has a lot of vitamin A. Context matters. Dose matters. Combination matters.

The same problem exists on the other side too. Chia seeds and flax seeds get promoted as omega-3 sources, and technically they are, but the omega-3 in them is ALA, and the conversion rate from ALA to the EPA and DHA your body actually uses is around 5-10% at best. That is not an argument against chia seeds. They are genuinely good for fibre and phytonutrients. It is an argument for knowing what a food actually does, rather than what someone on the internet says it does.

Most people don't need a drastic diet overhaul. They need better information. Someone eating chicken and chips isn't doing something terrible, they're one air fryer and a portion of vegetables away from a genuinely solid meal. That's the gap I wanted to close.

NutriCard is built around one idea: no food is inherently good or bad. Different foods have different strengths, and the goal is variety, balance, and understanding what you're actually eating.

---

## What it does

**Food cards.** Each food gets scored across five stats using real data from the USDA FoodData Central database. The scores reflect nutritional reality, sardines score high on protein and micronutrients, low on gut health, because sardines have no fibre. That's accurate, not a bug.

**Food roles.** Foods are assigned a role based on how they fit into a diet:
- **Daily Driver** - foundation foods you eat regularly (oats, sweet potato, eggs)
- **Weekly Anchor** - nutrient-dense foods with a natural ceiling (sardines, mackerel, liver)
- **Booster** - foods that enhance the nutrition of everything around them (lemon juice, black pepper, fermented foods)
- **Pantry** - small serving, big impact (garlic, onions, ginger, turmeric)
- **Occasional** - fine in context, not a daily staple

**Meal builder.** Combine foods into a meal and get a combined nutrition card. The scores are weighted by how many grams of each food you're using, 150g of sardines contributes more to the meal card than 10g of garlic.

**Synergy engine.** Some food combinations genuinely work better together. The API detects these and flags them:
- Sardines or mackerel with garlic or onion: Omega-3 + Allicin anti-inflammatory combination
- Oats with kiwi, lemon, or orange: Vitamin C reduces the phytic acid effect and improves mineral absorption
- Spinach with lemon or kiwi: Vitamin C improves iron absorption from plant sources
- High protein food with high fibre food: sustained energy and satiety

**Timing context.** A meal can be scored for a specific context: morning, pre-workout, post-workout, or evening. The same meal scores differently depending on when you eat it, because your nutritional needs shift throughout the day. Post-workout, protein matters more. Evening, gut health and micronutrients take priority.

---

## Tech stack

- Java 21
- Spring Boot 3.3
- PostgreSQL 17
- Spring Data JPA
- USDA FoodData Central API

---

## Running it locally

You need Java 21, Maven, and PostgreSQL installed.

**1. Clone the repo**
```bash
git clone https://github.com/KwesHub/nutricard-api.git
cd nutricard-api
```

**2. Create the database**
```bash
psql -U postgres
CREATE DATABASE nutricard_db;
\q
```

**3. Set your USDA API key**

Get a free key at https://fdc.nal.usda.gov/api-key-signup.html

```bash
export USDA_API_KEY=your_key_here
```

**4. Run**
```bash
mvn spring-boot:run
```

The app seeds three foods on startup (Sardines, Oats, Garlic) with real USDA data. On first run you'll see the tables being created and scores being calculated.

---

## Endpoints

```
GET  /foods                          List all foods
GET  /foods/{id}                     Get a food by ID
GET  /foods/{id}/card                Get a food's full nutrition card
POST /meals                          Create a meal from a list of foods
GET  /meals/{id}/card                Get a meal's combined nutrition card
```

**Example: Create a meal**
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

---

## What's coming

- Food search endpoint
- Food comparison (head-to-head stat breakdown)
- Anti-nutrient modifiers (phytic acid, oxalates, tannins affecting bioavailability scores)
- Fat-soluble vitamin meal modifier (fat present in the meal improves absorption of vitamins A, D, E, K)
- Culinary scoring layer (Salt, Acid, Fat, Heat, Umami — how well a meal is composed from a cooking standpoint)
- Food detail pages with prep notes, callouts, and suggested meals
- Frontend

---

## A note on the data

Nutritional data comes from the USDA FoodData Central SR Legacy dataset. This is a US government database, which means branded UK supermarket products are not covered. Nutrient profiles for generic whole foods (oats, sardines, garlic) are accurate regardless of geography. Branded product support and UK-specific data (McCance and Widdowson) is on the roadmap.
