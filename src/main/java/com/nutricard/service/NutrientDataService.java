package com.nutricard.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class NutrientDataService {

    private static final String BASE_URL = "https://api.nal.usda.gov";

    private static final Map<String, Integer> FDC_ID_MAP = Map.ofEntries(
            Map.entry("Sardines", 175139),
            Map.entry("Oats", 173904),
            Map.entry("Garlic", 169230),
            Map.entry("Eggs", 171287),
            Map.entry("Chicken breast", 171477),
            Map.entry("Beef mince 10%", 174036),
            Map.entry("Sweet potato", 168482),
            Map.entry("Brown rice", 169704),
            Map.entry("White rice", 169756),
            Map.entry("Pearl barley", 170283),
            Map.entry("Whole-wheat spaghetti", 170285),
            Map.entry("Red lentils", 172421),
            Map.entry("Green lentils", 172420),
            Map.entry("Red kidney beans", 175200),
            Map.entry("Peas", 170420),
            Map.entry("Spinach", 168462),
            Map.entry("Apple", 171688),
            Map.entry("Banana", 173944),
            Map.entry("Kiwi", 168153),
            Map.entry("Blueberries", 171711),
            Map.entry("Ginger", 169231),
            Map.entry("Honey", 169640),
            Map.entry("Peanut butter", 172470),
            Map.entry("Tahini", 168604),
            Map.entry("Olive oil", 171413),
            Map.entry("Dark chocolate 70%", 169593)
    );


    private final WebClient webClient;
    private final String apiKey;

    public NutrientDataService(@Value("${usda.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    public NutrientData fetchNutrientData(String foodName) {
        Integer fdcId = FDC_ID_MAP.get(foodName);
        if (fdcId != null) {
            NutrientData data = fetchByFdcId(fdcId, foodName);
            if (data != null) {
                return data;
            }
        }
        return fetchBySearch(foodName);
    }

    private NutrientData fetchByFdcId(int fdcId, String foodName) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fdc/v1/food/" + fdcId)
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                return null;
            }

            JsonNode foodNutrients = response.get("foodNutrients");
            if (foodNutrients == null || !foodNutrients.isArray()) {
                return null;
            }

            return extractNutrientData(foodName, foodNutrients, "nutrient", "name", "amount");

        } catch (Exception e) {
            System.err.println("Failed to fetch nutrient data by FDC ID for '" + foodName + "': " + e.getMessage());
            return null;
        }
    }

    private NutrientData fetchBySearch(String foodName) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fdc/v1/foods/search")
                            .queryParam("query", foodName)
                            .queryParam("api_key", apiKey)
                            .queryParam("pageSize", 1)
                            .queryParam("dataType", "SR Legacy")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                return null;
            }

            JsonNode foods = response.get("foods");
            if (foods == null || !foods.isArray() || foods.isEmpty()) {
                return null;
            }

            JsonNode foodNutrients = foods.get(0).get("foodNutrients");
            if (foodNutrients == null || !foodNutrients.isArray()) {
                return null;
            }

            return extractNutrientData(foodName, foodNutrients, null, "nutrientName", "value");

        } catch (Exception e) {
            System.err.println("Failed to fetch nutrient data for '" + foodName + "': " + e.getMessage());
            return null;
        }
    }

    private NutrientData extractNutrientData(String foodName, JsonNode foodNutrients, String nutrientObj, String nameField, String valueField) {
        double proteins = getNutrientValue(foodNutrients, "Protein", nutrientObj, nameField, valueField);
        double fiber = getNutrientValue(foodNutrients, "Fiber, total dietary", nutrientObj, nameField, valueField);
        double energyKcal = getNutrientValue(foodNutrients, "Energy", nutrientObj, nameField, valueField);
        double fat = getNutrientValue(foodNutrients, "Total lipid (fat)", nutrientObj, nameField, valueField);
        double saturatedFat = getNutrientValue(foodNutrients, "Fatty acids, total saturated", nutrientObj, nameField, valueField);
        double omega3 = getNutrientValue(foodNutrients, "Fatty acids, total polyunsaturated", nutrientObj, nameField, valueField);
        double sugars = getNutrientValue(foodNutrients, "Sugars, total including NLEA", nutrientObj, nameField, valueField);
        if (sugars == 0.0) sugars = getNutrientValue(foodNutrients, "Sugars, total", nutrientObj, nameField, valueField);
        if (sugars == 0.0) sugars = getNutrientValue(foodNutrients, "Total Sugars", nutrientObj, nameField, valueField);
        double mono = getNutrientValue(foodNutrients, "Fatty acids, total monounsaturated", nutrientObj, nameField, valueField);
        double poly = getNutrientValue(foodNutrients, "Fatty acids, total polyunsaturated", nutrientObj, nameField, valueField);

        double vitaminA = getNutrientValue(foodNutrients, "Vitamin A, RAE", nutrientObj, nameField, valueField);
        double vitaminC = getNutrientValue(foodNutrients, "Vitamin C, total ascorbic acid", nutrientObj, nameField, valueField);
        double vitaminD = getNutrientValue(foodNutrients, "Vitamin D (D2 + D3)", nutrientObj, nameField, valueField);
        double vitaminE = getNutrientValue(foodNutrients, "Vitamin E (alpha-tocopherol)", nutrientObj, nameField, valueField);
        double vitaminK = getNutrientValue(foodNutrients, "Vitamin K (phylloquinone)", nutrientObj, nameField, valueField);
        double vitaminB1 = getNutrientValue(foodNutrients, "Thiamin", nutrientObj, nameField, valueField);
        double vitaminB2 = getNutrientValue(foodNutrients, "Riboflavin", nutrientObj, nameField, valueField);
        double vitaminB3 = getNutrientValue(foodNutrients, "Niacin", nutrientObj, nameField, valueField);
        double vitaminB6 = getNutrientValue(foodNutrients, "Vitamin B-6", nutrientObj, nameField, valueField);
        double vitaminB12 = getNutrientValue(foodNutrients, "Vitamin B-12", nutrientObj, nameField, valueField);
        double folate = getNutrientValue(foodNutrients, "Folate, total", nutrientObj, nameField, valueField);

        double calcium = getNutrientValue(foodNutrients, "Calcium, Ca", nutrientObj, nameField, valueField);
        double iron = getNutrientValue(foodNutrients, "Iron, Fe", nutrientObj, nameField, valueField);
        double magnesium = getNutrientValue(foodNutrients, "Magnesium, Mg", nutrientObj, nameField, valueField);
        double phosphorus = getNutrientValue(foodNutrients, "Phosphorus, P", nutrientObj, nameField, valueField);
        double potassium = getNutrientValue(foodNutrients, "Potassium, K", nutrientObj, nameField, valueField);
        double zinc = getNutrientValue(foodNutrients, "Zinc, Zn", nutrientObj, nameField, valueField);
        double selenium = getNutrientValue(foodNutrients, "Selenium, Se", nutrientObj, nameField, valueField);
        double copper = getNutrientValue(foodNutrients, "Copper, Cu", nutrientObj, nameField, valueField);

        System.out.println("=== NUTRIENT DEBUG for " + foodName + " ===");
        System.out.println("Protein: " + proteins);
        System.out.println("Fiber: " + fiber);
        System.out.println("Energy: " + energyKcal);
        System.out.println("Sugars: " + sugars);
        System.out.println("Fat: " + fat);
        System.out.println("Saturated fat: " + saturatedFat);
        System.out.println("Mono fat: " + mono);
        System.out.println("Poly fat: " + poly);
        System.out.println("Vitamin A: " + vitaminA);
        System.out.println("Vitamin C: " + vitaminC);
        System.out.println("Vitamin D: " + vitaminD);
        System.out.println("Vitamin E: " + vitaminE);
        System.out.println("Vitamin K: " + vitaminK);
        System.out.println("B1: " + vitaminB1);
        System.out.println("B2: " + vitaminB2);
        System.out.println("B3: " + vitaminB3);
        System.out.println("B6: " + vitaminB6);
        System.out.println("B12: " + vitaminB12);
        System.out.println("Folate: " + folate);
        System.out.println("Calcium: " + calcium);
        System.out.println("Iron: " + iron);
        System.out.println("Magnesium: " + magnesium);
        System.out.println("Phosphorus: " + phosphorus);
        System.out.println("Potassium: " + potassium);
        System.out.println("Zinc: " + zinc);
        System.out.println("Selenium: " + selenium);
        System.out.println("Copper: " + copper);
        System.out.println("=== END DEBUG ===");

        return new NutrientData(proteins, fiber, energyKcal, fat, saturatedFat, omega3,
                sugars, mono, poly,
                vitaminA, vitaminC, vitaminD, vitaminE, vitaminK,
                vitaminB1, vitaminB2, vitaminB3, vitaminB6, vitaminB12, folate,
                calcium, iron, magnesium, phosphorus, potassium, zinc, selenium, copper);
    }

    private double getNutrientValue(JsonNode foodNutrients, String nutrientName,
                                     String nutrientObj, String nameField, String valueField) {
        for (JsonNode entry : foodNutrients) {
            JsonNode target = (nutrientObj != null) ? entry.get(nutrientObj) : entry;
            if (target == null) continue;

            JsonNode nameNode = target.get(nameField);
            if (nameNode != null && nutrientName.equals(nameNode.asText())) {
                JsonNode valueNode = (nutrientObj != null) ? entry.get(valueField) : entry.get(valueField);
                if (valueNode != null && !valueNode.isNull()) {
                    return valueNode.asDouble(0.0);
                }
            }
        }
        return 0.0;
    }

    public record NutrientData(
            double proteins100g,
            double fiber100g,
            double energyKcal100g,
            double fat100g,
            double saturatedFat100g,
            double omega3Fat100g,
            double sugars100g,
            double monounsaturatedFat100g,
            double polyunsaturatedFat100g,
            double vitaminA,
            double vitaminC,
            double vitaminD,
            double vitaminE,
            double vitaminK,
            double vitaminB1,
            double vitaminB2,
            double vitaminB3,
            double vitaminB6,
            double vitaminB12,
            double folate,
            double calcium,
            double iron,
            double magnesium,
            double phosphorus,
            double potassium,
            double zinc,
            double selenium,
            double copper
    ) {}
}
