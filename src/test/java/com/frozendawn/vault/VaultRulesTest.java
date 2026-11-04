package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The Unwatched Door's rules that need no world: what counts as facing, the bridge's reach, the clock. */
class VaultRulesTest {
    private static final Vec3 EYE = new Vec3(0, 0, 0), EAST = new Vec3(1, 0, 0);

    @Test
    void aFaceWithinThirtyFiveDegreesIsFacingAndOutsideIsNot() {
        double in = Math.tan(Math.toRadians(34)), out = Math.tan(Math.toRadians(36));
        assertTrue(VaultWatch.faces(EYE, EAST, new Vec3(5, 0, 0)));
        assertTrue(VaultWatch.faces(EYE, EAST, new Vec3(5, 0, 5 * in)));
        assertFalse(VaultWatch.faces(EYE, EAST, new Vec3(5, 0, 5 * out)));
        assertFalse(VaultWatch.faces(EYE, EAST, new Vec3(-5, 0, 0)));
    }

    @Test
    void aBodyOnTopOfTheTargetFacesNothing() {
        assertFalse(VaultWatch.faces(EYE, EAST, EYE));
    }

    @Test
    void theBridgeReachesTwoCellsPastWhoeverStandsOnIt() {
        assertTrue(VaultBridge.wants(0, -2), "From the back of the ledge the first cell comes");
        assertFalse(VaultBridge.wants(1, -2));
        assertTrue(VaultBridge.wants(1, -1));
        assertFalse(VaultBridge.wants(2, -1), "From the ledge's lip no further than two cells");
        assertTrue(VaultBridge.wants(3, 1));
        assertFalse(VaultBridge.wants(4, 1));
    }

    @Test
    void theBridgeStopsAtTheFarSide() {
        assertTrue(VaultBridge.wants(VaultBridge.SPAN - 1, VaultBridge.SPAN - 2));
        assertFalse(VaultBridge.wants(VaultBridge.SPAN, VaultBridge.SPAN - 1));
    }

    @Test
    void theClockRunsBackOneASecond() {
        long start = 3 * 3600 + 17 * 60 + 42;
        assertEquals("03:17:42", VaultClock.text(start, 0));
        assertEquals("03:17:41", VaultClock.text(start, 20));
        assertEquals("03:17:41", VaultClock.text(start, 39));
        assertEquals("03:17:40", VaultClock.text(start, 40));
    }

    @Test
    void theClockNeverGoesBelowZeroAndPassesThroughTheTopAgain() {
        assertEquals("00:00:00", VaultClock.text(5, 100));
        assertEquals("99:59:59", VaultClock.text(5, 120));
    }

    @Test
    void theClockStartIsFixedBySeedAndInRange() {
        assertEquals(VaultClock.start(42), VaultClock.start(42));
        assertNotEquals(VaultClock.start(42), VaultClock.start(43));
        for (long seed = 0; seed < 50; seed++) assertTrue(VaultClock.start(seed) >= 0 && VaultClock.start(seed) < VaultClock.CYCLE);
    }
}
