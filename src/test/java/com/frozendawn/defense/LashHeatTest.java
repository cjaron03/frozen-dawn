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

    @Test
    void aLockoutFromALaterClockDoesNotStick() {
        // Carried from a world whose game time is far ahead, the stored lockout lies in this world's distant future.
        var hot = fire(LashHeat.COOL, 1_000_000, LashHeat.FIRE_TICKS, false);
        assertFalse(hot.lockedOut(5000), "No lockout lasts longer than the vacuum maximum");
        assertTrue(hot.fire(5000, false).fired());
    }

    @Test
    void theGlowFollowsHeatAndCoolsWhenIdle() {
        assertEquals(0, LashHeat.COOL.glow(5000));
        var half = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS / 2, false);
        long last = 1000 + LashHeat.FIRE_TICKS / 2 - 1;
        assertEquals(.5F, half.glow(last), 1e-6, "Three seconds of fire is half way to overheat");
        assertEquals(.25F, half.glow(last + LashHeat.FIRE_TICKS / 4), 1e-6, "Idle, it sheds heat as fast as firing adds it");
        assertEquals(0, half.glow(last + LashHeat.FIRE_TICKS), "and goes cold");
    }

    @Test
    void anOverheatedLashGlowsFullAndVentsWhiteThenCoolsRed() {
        var hot = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS, false);
        long tripped = 1000 + LashHeat.FIRE_TICKS - 1;
        assertEquals(1F, hot.glow(tripped + 1));
        assertEquals(1F, hot.vent(tripped + 1), "Just tripped: vented white-hot");
        assertEquals(.5F, hot.vent(tripped + LashHeat.LOCKOUT_TICKS / 2), "The second half of the lockout cools");
        assertEquals(0, hot.vent(tripped + LashHeat.LOCKOUT_TICKS), "Vents shut when the lockout ends");
        assertEquals(0, hot.glow(tripped + LashHeat.LOCKOUT_TICKS), "and the emitter is cool again");
        var vacuum = fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS, true);
        assertEquals(1F, vacuum.vent(tripped + LashHeat.LOCKOUT_TICKS / 2 + 1), "In vacuum each half lasts twice as long");
        assertEquals(.5F, vacuum.vent(tripped + LashHeat.LOCKOUT_TICKS + 1));
    }

    @Test
    void aFiringLashNeverVents() {
        assertEquals(0, fire(LashHeat.COOL, 1000, LashHeat.FIRE_TICKS - 1, false).vent(1000 + LashHeat.FIRE_TICKS - 2));
    }
}
