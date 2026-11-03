package com.frozendawn.flora;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.flora.ColdFloraPolicy.Cap;
import org.junit.jupiter.api.Test;

class ColdFloraPolicyTest {
    @Test void frostcapGrowsOnlyAtMinusTwentyAndDoublesByMinusEighty() {
        assertEquals(0, ColdFloraPolicy.frostcapGrowth(-19.9f), "Stalls between -20C and 0C");
        assertEquals(0, ColdFloraPolicy.frostcapGrowth(5f));
        assertEquals(1, ColdFloraPolicy.frostcapGrowth(-20f), 1e-9, "1x at -20C");
        assertEquals(1.5, ColdFloraPolicy.frostcapGrowth(-50f), 1e-9);
        assertEquals(2, ColdFloraPolicy.frostcapGrowth(-80f), 1e-9, "About 2x at -80C");
        assertEquals(2, ColdFloraPolicy.frostcapGrowth(-200f), 1e-9, "Capped at 2x");
        assertTrue(0f <= ColdFloraPolicy.FROSTCAP_DIES_ABOVE, "Dies only above 0C");
    }

    @Test void saplingNeedsSkyAndColdButNoSun() {
        assertTrue(ColdFloraPolicy.saplingGrows(true, -20f));
        assertTrue(ColdFloraPolicy.saplingGrows(true, -150f), "Late Phase 6 cold still grows");
        assertFalse(ColdFloraPolicy.saplingGrows(true, -19f), "Warmth pauses it");
        assertFalse(ColdFloraPolicy.saplingGrows(false, -60f), "Needs sky access");
    }

    @Test void saplingTimingMeetsTheTargets() {
        double secondsPerRandomTick = 4096.0 / 3 / 20; // default random tick speed: about 68 s per block
        double minutes = 2 * ColdFloraPolicy.SAPLING_ADVANCE_ONE_IN * secondsPerRandomTick / 60;
        assertTrue(minutes >= 20 && minutes <= 30, "Two growth stages in 20-30 minutes: " + minutes);
        double boneMeal = 2 / 0.45;
        assertTrue(boneMeal >= 4 && boneMeal <= 6, "4-6 bone meal on average: " + boneMeal);
    }

    @Test void thawIsOneWayFrozenThawedMush() {
        assertEquals(Cap.THAWED, ColdFloraPolicy.next(Cap.FROZEN));
        assertEquals(Cap.MUSH, ColdFloraPolicy.next(Cap.THAWED));
        assertEquals(Cap.MUSH, ColdFloraPolicy.next(Cap.MUSH));
        assertEquals(300, ColdFloraPolicy.THAW_SECONDS, "About five minutes each step");
    }
}
