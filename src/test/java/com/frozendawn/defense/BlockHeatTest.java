package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BlockHeatTest {
    @Test
    void aHeldBeamGivesWayOnTheLastTickOfItsDwell() {
        int heat = 0; long last = Long.MIN_VALUE;
        for (int t = 0; t < BlockHeat.PACKED_ICE_TICKS - 1; t++) {
            heat = BlockHeat.land(heat, last, 1000 + t); last = 1000 + t;
            assertFalse(BlockHeat.gives(heat, BlockHeat.PACKED_ICE_TICKS), "Still holding after " + (t + 1) + " ticks");
        }
        heat = BlockHeat.land(heat, last, 1000 + BlockHeat.PACKED_ICE_TICKS - 1);
        assertTrue(BlockHeat.gives(heat, BlockHeat.PACKED_ICE_TICKS));
    }

    @Test
    void aSweepPastLeavesTheBlock() {
        assertFalse(BlockHeat.gives(BlockHeat.land(0, Long.MIN_VALUE, 1000), BlockHeat.STONE_TICKS));
    }

    @Test
    void severalRaysInOneTickHeatOnce() {
        int once = BlockHeat.land(0, Long.MIN_VALUE, 1000);
        assertEquals(once, BlockHeat.land(once, 1000, 1000));
    }

    @Test
    void anIdleBlockCoolsAtHalfTheRateItHeats() {
        int heat = BlockHeat.land(0, Long.MIN_VALUE, 1000);
        heat = BlockHeat.land(heat, 1000, 1001);
        assertEquals(2 * BlockHeat.PER_TICK, heat);
        // Two idle ticks shed one tick's heat.
        assertEquals(2 * BlockHeat.PER_TICK, BlockHeat.land(heat, 1001, 1004));
        assertTrue(BlockHeat.cold(heat, 1001, 1005), "Four units shed over four idle ticks");
        assertFalse(BlockHeat.cold(heat, 1001, 1002));
    }

    @Test
    void blueIceTakesTwiceAsLongAsPackedIce() {
        assertEquals(2 * BlockHeat.PACKED_ICE_TICKS, BlockHeat.BLUE_ICE_TICKS);
        assertTrue(BlockHeat.STONE_TICKS < BlockHeat.PACKED_ICE_TICKS);
    }
}
