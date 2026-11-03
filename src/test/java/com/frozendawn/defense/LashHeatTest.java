package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LashHeatTest {
    private static LashHeat fire(LashHeat heat, long from, int ticks, boolean vacuum) {
        for (long t = from; t < from + ticks; t++) heat = heat.fire(t, vacuum).heat();
        return heat;
    }

    @Test
    void sixSecondsOfContinuousFireOverheats() {
        var almost = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS - 1, false);
        assertFalse(almost.lockedOut(1000 + LashHeat.FIRE_TICKS - 1), "119 ticks is still firing");
        var shot = almost.fire(1000 + LashHeat.FIRE_TICKS - 1, false);
        assertTrue(shot.fired(), "The overheating tick itself still cuts");
        assertTrue(shot.heat().lockedOut(1000 + LashHeat.FIRE_TICKS), "The 120th tick locks the emitter out");
    }

    @Test
    void lockoutLastsFourSecondsInAirAndCostsNothing() {
        long end = 1000 + LashHeat.FIRE_TICKS;
        var hot = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS, false);
        var refused = hot.fire(end, false);
        assertFalse(refused.fired(), "A locked emitter does not fire");
        assertEquals(hot, refused.heat(), "Refusal leaves the state untouched");
        assertTrue(hot.lockedOut(end + 79 - 1));
        assertFalse(hot.lockedOut(end - 1 + LashHeat.LOCKOUT_TICKS), "Four seconds clears the lockout");
        var after = hot.fire(end - 1 + LashHeat.LOCKOUT_TICKS, false);
        assertTrue(after.fired(), "It fires again from cold");
        assertEquals(LashHeat.FIRE_HEAT, after.heat().heat(), "Lockout vents all heat");
    }

    @Test
    void vacuumDoublesTheLockout() {
        long last = 1000 + LashHeat.FIRE_TICKS - 1;
        var hot = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS, true);
        assertTrue(hot.lockedOut(last + LashHeat.LOCKOUT_TICKS), "Air lockout is not enough without air");
        assertFalse(hot.lockedOut(last + 2 * LashHeat.LOCKOUT_TICKS));
    }

    @Test
    void idleTicksCoolAsFastAsFiringHeatsInAir() {
        var warm = fire(LashHeat.COOL, 1000, 100, false);
        // A 50-tick pause sheds 50 ticks of fire, so 70 more ticks reach the limit.
        var resumed = fire(warm, 1150, 69, false);
        assertFalse(resumed.lockedOut(1219));
        assertTrue(resumed.fire(1219, false).heat().lockedOut(1220));
    }

    @Test
    void vacuumCoolsAtHalfRate() {
        var warm = fire(LashHeat.COOL, 1000, 100, true);
        // The same pause sheds only 25 ticks of fire.
        var resumed = fire(warm, 1150, 44, true);
        assertFalse(resumed.lockedOut(1194));
        assertTrue(resumed.fire(1194, true).heat().lockedOut(1195));
    }

    @Test
    void heatNeverGoesNegative() {
        var warm = fire(LashHeat.COOL, 1000, 10, false);
        assertEquals(LashHeat.FIRE_HEAT, warm.fire(100_000, false).heat().heat());
    }
}
