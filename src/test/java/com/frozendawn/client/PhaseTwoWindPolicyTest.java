package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTwoWindPolicyTest {
    @Test void phaseChangesAndSuppressionCannotLeakTheNewWindIntoOtherContexts() {
        for (int phase = -1; phase <= 7; phase++) {
            float gain = PhaseTwoWindPolicy.gain(phase, .17F, 6000, 1, false);
            assertEquals(phase == 2, gain > 0);
            assertEquals(0, PhaseTwoWindPolicy.gain(phase, .17F, 6000, 1, true));
        }
        assertEquals(0, PhaseTwoWindPolicy.gain(2, Float.NaN, 0, 1, false));
        assertEquals(0, PhaseTwoWindPolicy.gain(2, .17F, 0, Float.NaN, false));
    }

    @Test void coldWindContinuesAcrossDayNightAndClockWrapsWithinItsQuietRange() {
        for (long time = 0; time <= 48000; time += 17) {
            for (float progress : new float[]{.12F, .17F, .22F}) {
                float gain = PhaseTwoWindPolicy.gain(2, progress, time, 1, false);
                assertTrue(gain >= .23F && gain <= .33F);
                assertTrue(Math.abs(gain - PhaseTwoWindPolicy.gain(2, progress, time + 1, 1, false)) < .001F);
            }
        }
    }

    @Test void shelterRetainsOnlyAFaintBedInsteadOfMutingWeatherAbruptly() {
        float outside = PhaseTwoWindPolicy.gain(2, .17F, 9000, 1, false);
        float inside = PhaseTwoWindPolicy.gain(2, .17F, 9000, 0, false);
        assertEquals(outside * .07F, inside, .00001F);
        assertEquals(inside, PhaseTwoWindPolicy.gain(2, .17F, 9000, -1, false));
        assertEquals(outside, PhaseTwoWindPolicy.gain(2, .17F, 9000, 2, false));
    }
}
