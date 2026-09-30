package com.nutricard.service;

import com.nutricard.service.NutrientDataService.NutrientData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutrientDataServiceTest {

    // Gives every field a different value (1, 2, 3, ...) so a swapped pair would show up
    private NutrientData numbered() {
        double[] v = new double[34];
        for (int i = 0; i < v.length; i++) v[i] = i + 1;
        return new NutrientData(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17],
                v[18], v[19], v[20], v[21], v[22], v[23], v[24], v[25], v[26], v[27],
                v[28], v[29], v[30], v[31], v[32], v[33]);
    }

    @Test
    void cookedCorrectionChangesOnlyProteinEnergyAndFibre() throws Exception {
        NutrientData before = numbered();
        NutrientData after = before.withProteinEnergyFibre(100, 200, 300);

        for (RecordComponent c : NutrientData.class.getRecordComponents()) {
            double was = (double) c.getAccessor().invoke(before);
            double now = (double) c.getAccessor().invoke(after);
            switch (c.getName()) {
                case "proteins100g" -> assertEquals(100, now, 0.0);
                case "energyKcal100g" -> assertEquals(200, now, 0.0);
                case "fiber100g" -> assertEquals(300, now, 0.0);
                default -> assertEquals(was, now, 0.0, c.getName() + " should be unchanged");
            }
        }
    }
}
