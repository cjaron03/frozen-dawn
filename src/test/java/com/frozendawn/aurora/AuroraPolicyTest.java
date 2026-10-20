package com.frozendawn.aurora;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuroraPolicyTest {
    @Test void nightlyEventsReconstructWithoutSessionState() {
        for (long day = 0; day < 100; day++) {
            long time = day * 24_000 + 18_000;
            var first = AuroraPolicy.nightly(471L, time, 1, 0.08F);
            assertEquals(first, AuroraPolicy.nightly(471L, time + 20, 1, 0.08F));
            if (first.durationTicks() > 0) {
                assertTrue(first.startTick() >= day * 24_000 + 13_300);
                assertTrue(first.startTick() + first.durationTicks() < day * 24_000 + 22_000);
            }
        }
    }

    @Test void phaseOneIsTheBestWindowAndLatePhasesHaveNoEvents() {
        int[] counts = new int[4];
        for (int day = 0; day < 2000; day++) {
            for (int phase = 0; phase < 4; phase++) {
                if (AuroraPolicy.nightly(471L, day * 24_000L, phase, 0.25F).durationTicks() > 0) counts[phase]++;
            }
        }
        assertTrue(counts[1] > counts[2] && counts[2] > counts[0] && counts[0] > counts[3]);
        assertEquals(AuroraPolicy.Display.NONE, AuroraPolicy.nightly(471L, 18_000, 4, 0.34F));
        assertEquals(0F, AuroraPolicy.phaseStrength(3, 0.34F));
    }

    @Test void daylightAndEventBoundariesFadeToZero() {
        assertEquals(0F, AuroraPolicy.darkness(6000, 0));
        assertEquals(1F, AuroraPolicy.darkness(18_000, 0));
        assertEquals(0F, AuroraPolicy.envelope(-1, 6000));
        assertEquals(0F, AuroraPolicy.envelope(0, 6000));
        assertEquals(1F, AuroraPolicy.envelope(3000, 6000));
        assertEquals(0F, AuroraPolicy.envelope(6000, 6000));
        assertTrue(AuroraPolicy.envelope(10, 6000) < 0.001F);
        assertTrue(AuroraPolicy.envelope(5990, 6000) < 0.001F);
    }

    @Test void StormDarknessDoesNotCreateDaytimeAurorasAndWeatherStillAttenuatesWithCloudsOff() {
        assertEquals(0F, AuroraPolicy.darkness(6000, 0) * AuroraPolicy.weatherVisibility(1, 1));
        assertTrue(AuroraPolicy.weatherVisibility(1, 1) < 0.05F);
        assertTrue(AuroraPolicy.weatherVisibility(1, 0) < AuroraPolicy.weatherVisibility(0, 0));
    }
}
