package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseOneWindExposureTest {
    @Test void joiningIndoorsOrOutdoorsStartsAtTheActualCoverRatherThanAnArtificialSweep() {
        PhaseOneWindExposure blend = new PhaseOneWindExposure();
        assertEquals(0, blend.tick(false));
        blend.reset();
        assertEquals(1, blend.tick(true));
        blend.reset();
        assertEquals(0, blend.tick(false));
    }

    @Test void shelteringAndReturningOutsideTakeSecondsAndRemainBoundedAndContinuous() {
        PhaseOneWindExposure blend = new PhaseOneWindExposure();
        float previous = blend.tick(true);
        for (int tick = 1; tick <= 50; tick++) {
            float value = blend.tick(false);
            assertTrue(value >= 0 && value <= previous);
            assertTrue(previous - value < .04F, "No roof-edge volume step");
            if (tick == 25) assertEquals(.5F, value, .01F);
            previous = value;
        }
        assertEquals(0, previous, .0001F);
        for (int tick = 1; tick <= 40; tick++) {
            float value = blend.tick(true);
            assertTrue(value >= previous && value <= 1);
            assertTrue(value - previous < .04F);
            if (tick == 20) assertEquals(.5F, value, .01F);
            previous = value;
        }
        assertEquals(1, previous, .0001F);
    }

    @Test void reversingAtADoorwayContinuesFromCurrentMixRatherThanRestartingAtAnEndpoint() {
        PhaseOneWindExposure blend = new PhaseOneWindExposure();
        float previous = blend.tick(true);
        for (int tick = 0; tick < 300; tick++) {
            float value = blend.tick((tick / 7) % 2 == 1);
            assertTrue(value >= 0 && value <= 1);
            assertTrue(Math.abs(value - previous) < .04F);
            previous = value;
        }
        for (int tick = 0; tick < 60; tick++) previous = blend.tick(false);
        assertEquals(0, previous);
    }
}
