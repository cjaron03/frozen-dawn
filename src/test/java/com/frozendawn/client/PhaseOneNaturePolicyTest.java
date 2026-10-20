package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseOneNaturePolicyTest {
    @Test void onlyPhasesZeroAndOneCompatibleBiomesAndUnsuppressedContextsMayStartPhrases() {
        for (int phase = -1; phase <= 7; phase++) {
            assertEquals(phase >= 0 && phase <= 1, PhaseOneNaturePolicy.eligible(phase, true, false));
            assertFalse(PhaseOneNaturePolicy.eligible(phase, false, false));
            assertFalse(PhaseOneNaturePolicy.eligible(phase, true, true));
        }
    }

    @Test void rainFlagAndEvenSmallWeatherFadesPreventNewPhrases() {
        assertTrue(PhaseOneNaturePolicy.dry(false, 0, 0));
        assertFalse(PhaseOneNaturePolicy.dry(true, 0, 0), "Rain flag precedes interpolation");
        assertFalse(PhaseOneNaturePolicy.dry(false, .0001F, 0), "Wait until rain has fully cleared");
        assertFalse(PhaseOneNaturePolicy.dry(false, 0, .0001F));
        assertFalse(PhaseOneNaturePolicy.dry(true, 1, 1));
    }

    @Test void nightAndShelterSilenceWindWithContinuousDaylightFades() {
        assertTrue(PhaseOneNaturePolicy.volume(.057F, 6000, 1) > .3F);
        for (long time : new long[]{0, 13000, 18000, 23999})
            assertEquals(0, PhaseOneNaturePolicy.volume(.057F, time, 1));
        assertEquals(0, PhaseOneNaturePolicy.volume(.057F, 6000, 0));
        float previous = 0;
        for (long time = 0; time <= 1000; time++) {
            float next = PhaseOneNaturePolicy.volume(.057F, time, 1);
            assertTrue(next >= previous && next - previous < .001F);
            previous = next;
        }
        assertEquals(PhaseOneNaturePolicy.volume(.057F, 6000, 1),
                PhaseOneNaturePolicy.volume(.057F, 30000, 1));
        assertTrue(PhaseOneNaturePolicy.volume(.1151F, 6000, 1)
                < PhaseOneNaturePolicy.volume(.057F, 6000, 1));
    }

    @Test void silentGapsAreBoundedAndGrowAsThePhaseGetsColder() {
        for (int i = 0; i <= 100; i++) {
            float roll = i / 100F;
            int early = PhaseOneNaturePolicy.gapTicks(.05F, roll);
            int late = PhaseOneNaturePolicy.gapTicks(.12F, roll);
            assertTrue(early >= 160 && early <= 320);
            assertTrue(late >= 240 && late <= 400);
            assertTrue(late > early);
        }
    }
}
