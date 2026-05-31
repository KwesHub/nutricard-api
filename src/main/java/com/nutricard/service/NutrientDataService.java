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

    private static final String[] VITAMINS = {
            "Vitamin A, RAE", "Vitamin C, total ascorbic acid", "Vitamin D (D2 + D3)",
            "Vitamin E (alpha-tocopherol)", "Vitamin K (phylloquinone)", "Vitamin B-12", "Folate, total"
    };

    private static final String[] MINERALS = {
            "Iron, Fe", "Zinc, Zn", "Magnesium, Mg",
            "Calcium, Ca", "Potassium, K", "Selenium, Se"
    };

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

            return extractNutrientData(foodNutrients, "nutrient", "name", "amount");

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

            return extractNutrientData(foodNutrients, null, "nutrientName", "value");

        } catch (Exception e) {
            System.err.println("Failed to fetch nutrient data for '" + foodName + "': " + e.getMessage());
            return null;
        }
    }

    private NutrientData extractNutrientData(JsonNode foodNutrients, String nutrientObj, String nameField, String valueField) {
        double proteins = getNutrientValue(foodNutrients, "Protein", nutrientObj, nameField, valueField);
        double fiber = getNutrientValue(foodNutrients, "Fiber, total dietary", nutrientObj, nameField, valueField);
        double energyKcal = getNutrientValue(foodNutrients, "Energy", nutrientObj, nameField, valueField);
        double fat = getNutrientValue(foodNutrients, "Total lipid (fat)", nutrientObj, nameField, valueField);
        double saturatedFat = getNutrientValue(foodNutrients, "Fatty acids, total saturated", nutrientObj, nameField, valueField);
        double omega3 = getNutrientValue(foodNutrients, "Fatty acids, total polyunsaturated", nutrientObj, nameField, valueField);

        int vitaminCount = countPresent(foodNutrients, VITAMINS, nutrientObj, nameField, valueField);
        int mineralCount = countPresent(foodNutrients, MINERALS, nutrientObj, nameField, valueField);

        return new NutrientData(proteins, fiber, energyKcal, fat, saturatedFat, omega3, vitaminCount, mineralCount);
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

    private int countPresent(JsonNode foodNutrients, String[] nutrientNames,
                              String nutrientObj, String nameField, String valueField) {
        int count = 0;
        for (String name : nutrientNames) {
            double value = getNutrientValue(foodNutrients, name, nutrientObj, nameField, valueField);
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    public record NutrientData(
            double proteins100g,
            double fiber100g,
            double energyKcal100g,
            double fat100g,
            double saturatedFat100g,
            double omega3Fat100g,
            int vitaminCount,
            int mineralCount
    ) {}
}
