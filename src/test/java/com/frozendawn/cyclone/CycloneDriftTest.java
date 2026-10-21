package com.frozendawn.cyclone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycloneDriftTest {
    @Test void shadowTapersAndCannotExceedOneBlock() {
        assertEquals(5, CycloneDrift.shadowDepth(3, 1));
        assertEquals(1, CycloneDrift.shadowDepth(3, 5));
        assertEquals(0, CycloneDrift.shadowDepth(3, 6));
        assertEquals(0, CycloneDrift.shadowDepth(3, 7));
        assertEquals(8, CycloneDrift.shadowDepth(6, 2));
        for (int height = -2; height <= 100; height++) for (int distance = -1; distance <= 10; distance++) {
            int layers = CycloneDrift.shadowDepth(height, distance);
            assertTrue(layers >= 0 && layers <= 8);
            assertTrue(layers <= CycloneDrift.shadowDepth(height + 1, distance));
        }
        assertEquals(0, CycloneDrift.shadowDepth(0, 1));
        assertEquals(0, CycloneDrift.shadowDepth(4, 0));
    }
    @Test void budgetOnlyRunsInActiveSnowStagesAndStaysBounded() {
        for (var stage : CycloneStage.values()) for (float intensity : new float[]{0, .1f, .5f, 1, 2}) {
            int budget = CycloneDrift.budgetPerTick(new CycloneSample(stage, intensity, 0, 0, 1000));
            boolean active = stage == CycloneStage.ARRIVAL || stage == CycloneStage.PEAK || stage == CycloneStage.DISSIPATION;
            assertEquals(active ? Math.round(6 * Math.clamp(intensity, 0, 1)) : 0, budget);
            assertTrue(budget >= 0 && budget <= 6);
        }
        assertEquals(0, CycloneDrift.budgetPerTick(CycloneSample.NONE));
    }
}
