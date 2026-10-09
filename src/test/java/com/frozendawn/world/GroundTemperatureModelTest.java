package com.frozendawn.world;

import com.frozendawn.phase.SurfaceTemperatureCurve;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundTemperatureModelTest {
    @Test
    void matchesIndependentHighResolutionErfcReferenceAcrossAllPresets() throws Exception {
        try (var input = getClass().getResourceAsStream("/thermal/ground-reference.csv")) {
            assertNotNull(input);
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            int samples = 0;
            for (String row : reader.lines().skip(1).toList()) {
                String[] fields = row.split(",");
                double y = Double.parseDouble(fields[0]), p = Double.parseDouble(fields[1]);
                double scale = Double.parseDouble(fields[2]), geothermal = Double.parseDouble(fields[3]);
                double a = Double.parseDouble(fields[4]), expected = Double.parseDouble(fields[5]);
                assertEquals(expected, GroundTemperatureModel.temperature(y, p, geothermal, scale, a), .025, row);
                samples++;
            }
            assertEquals(60, samples);
        }
    }

    @Test
    void surfaceAndFractionalDepthHaveNoTwelveDegreeDiscontinuity() {
        for (double scale : new double[]{2d/3, 1, 4d/3}) {
            for (int k = 0; k <= 1000; k++) {
                double p = k / 1000d;
                double surface = SurfaceTemperatureCurve.temperature((float) p, (float) scale);
                assertEquals(surface, temperature(64, p, scale), 0);
                assertEquals(surface, temperature(64 - .000001, p, scale), .001);
            }
        }
    }

    @Test
    void groundStartsGeothermalAndEndRefugePersistsAfterProgressClamps() {
        assertEquals(38.4, temperature(-64, 0, 1), .00001);
        double end = temperature(-64, 1, 1);
        assertTrue(end > 0 && end < 15);
        assertEquals(end, temperature(-64, 2, 1), 0);
        assertEquals(temperature(-64, 0, 1), temperature(-64, -1, 1), 0);
        assertEquals(end, temperature(-200, 1, 1), 0);
    }

    @Test
    void largerDiffusivityMakesDeepGroundColderAndColdReachesShallowsFirst() {
        for (double p : new double[]{.46, .60, .85, 1}) {
            double shallow = temperature(32, p, 1);
            double deep = temperature(-64, p, 1);
            assertTrue(deep > shallow);
            assertTrue(GroundTemperatureModel.temperature(-64, p, 1, 1, 12000) < deep);
        }
    }

    @Test
    void scalesCoolingAndGeothermalSeparatelyWithoutScalingFalseCalm() {
        assertEquals(-157, temperature(64, .60, 4d/3), .0001);
        double warmRock = GroundTemperatureModel.temperature(-64, .85, 1.5, 1, 8000);
        double standard = temperature(-64, .85, 1);
        assertEquals(19.2, warmRock - standard, .00001);
    }

    @Test
    void invalidParametersAreRejectedRatherThanPoisoningTheSharedCache() {
        assertThrows(IllegalArgumentException.class, () -> temperature(0, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> GroundTemperatureModel.temperature(0, .5, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> GroundTemperatureModel.temperature(0, .5, -1, 1, 8000));
    }

    @Test
    void pureSurfaceCurveRetainsExistingAnchorsAndFalseCalm() {
        float[] progress = {0, .05f, .12f, .22f, .34f, .46f, .60f, 1};
        float[] base = {0, 0, -10, -25, -45, -70, -120, -273};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(base[i], SurfaceTemperatureCurve.base(progress[i]), .0001);
            assertEquals(i > 0 && i < 7 ? 3 : 0, SurfaceTemperatureCurve.rebound(progress[i]), .0001);
        }
        assertEquals(-215.625, SurfaceTemperatureCurve.temperature(.85f, 1), .0001);
    }

    private static double temperature(double y, double p, double scale) {
        return GroundTemperatureModel.temperature(y, p, 1, scale, 8000);
    }
}
