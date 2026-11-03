package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseFogColorPolicyTest {
    private static final float MIDNIGHT = (float) Math.PI;

    @Test void fullNightHasNoAmberOrDaylitHorizonInPhasesOneThroughFour() {
        for (int phase = 1; phase <= 4; phase++) {
            var night = PhaseFogColorPolicy.target(phase, 1.0F, 1.0F, MIDNIGHT);
            var noon = PhaseFogColorPolicy.target(phase, 1.0F, 1.0F, 0.0F);
            assertTrue(night.blue() >= night.red(), "No warm night target in phase " + phase);
            assertTrue(Math.max(night.red(), Math.max(night.green(), night.blue())) < 0.03F);
            assertTrue(night.blue() < noon.blue() * 0.1F);
        }
    }

    @Test void daytimeRetainsTheExistingPhasePaletteAndBrightness() {
        var amber = PhaseFogColorPolicy.target(1, 1.0F, 1.0F, 0.0F);
        assertEquals(0.85F, amber.red(), 0.000001F);
        assertEquals(0.55F, amber.green(), 0.000001F);
        assertEquals(0.35F, amber.blue(), 0.000001F);
        var cold = PhaseFogColorPolicy.target(3, 0.5F, 0.4F, 0.0F);
        assertEquals(0.25F * 0.29F, cold.red(), 0.000001F);
        assertEquals(0.55F * 0.29F, cold.blue(), 0.000001F);
    }

    @Test void twilightChangesContinuouslyInBothDirections() {
        for (int phase = 1; phase <= 4; phase++) {
            float previous = PhaseFogColorPolicy.target(phase, 1, 1, 0).red();
            for (int step = 1; step <= 1000; step++) {
                float angle = MIDNIGHT * step / 1000;
                var dusk = PhaseFogColorPolicy.target(phase, 1, 1, angle);
                var dawn = PhaseFogColorPolicy.target(phase, 1, 1, 2 * MIDNIGHT - angle);
                assertTrue(dusk.red() <= previous + 0.000001F);
                assertTrue(Math.abs(dusk.red() - previous) < 0.015F, "No sunset/night switch");
                assertEquals(dusk.red(), dawn.red(), 0.000005F);
                assertEquals(dusk.blue(), dawn.blue(), 0.000005F);
                previous = dusk.red();
            }
        }
    }

    @Test void LateAtmosphereTargetsKeepTheirExistingFloorsAndCaps() {
        for (int phase = 5; phase <= 6; phase++) {
            assertEquals(PhaseFogColorPolicy.target(phase, 1, 1, 0),
                    PhaseFogColorPolicy.target(phase, 1, 1, MIDNIGHT));
        }
        assertEquals(0.006F, PhaseFogColorPolicy.target(5, 1, 1, MIDNIGHT).blue(), 0.000001F);
        assertEquals(0.0002F, PhaseFogColorPolicy.target(6, 0, 0, MIDNIGHT).blue(), 0.000001F);
    }
}
