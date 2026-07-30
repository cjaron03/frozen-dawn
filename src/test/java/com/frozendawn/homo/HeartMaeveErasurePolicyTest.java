package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartMaeveErasurePolicyTest {
    @Test
    void unmakingProgressHasStableBoundaries() {
        assertEquals(0.0F, HeartMaeveErasurePolicy.progress(-20L));
        assertEquals(0.5F, HeartMaeveErasurePolicy.unmakingProgress(60L), 0.0001F);
        assertEquals(1.0F, HeartMaeveErasurePolicy.unmakingProgress(120L));
        assertEquals(0.0F, HeartMaeveErasurePolicy.forgingProgress(120L));
        assertEquals(0.5F, HeartMaeveErasurePolicy.forgingProgress(170L), 0.0001F);
        assertFalse(HeartMaeveErasurePolicy.complete(219L));
        assertTrue(HeartMaeveErasurePolicy.complete(220L));
    }

    @Test
    void deliberateChannelOutlastsAnAccidentalUsePress() {
        assertEquals(80, HeartMaeveErasurePolicy.CHANNEL_TICKS);
        assertEquals(120, HeartMaeveErasurePolicy.UNMAKING_TICKS);
        assertEquals(100, HeartMaeveErasurePolicy.FORGING_TICKS);
        assertEquals(220, HeartMaeveErasurePolicy.TOTAL_TICKS);
    }
}
