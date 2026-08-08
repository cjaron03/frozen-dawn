package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMaeveWorldStateTest {
    @Test
    void bloomReleasesExactlyTenSecondsAfterBiologicalWarning() {
        long warningAt = 12_000L;
        assertFalse(PostMaeveWorldState.bloomDelayElapsed(warningAt, warningAt));
        assertFalse(PostMaeveWorldState.bloomDelayElapsed(
                warningAt + PostMaeveWorldState.BLOOM_RELEASE_DELAY_TICKS - 1L,
                warningAt));
        assertTrue(PostMaeveWorldState.bloomDelayElapsed(
                warningAt + PostMaeveWorldState.BLOOM_RELEASE_DELAY_TICKS,
                warningAt));
    }

    @Test
    void missingOrRolledBackClockNeverReleasesBloomEarly() {
        assertFalse(PostMaeveWorldState.bloomDelayElapsed(20_000L, -1L));
        assertFalse(PostMaeveWorldState.bloomDelayElapsed(9_000L, 10_000L));
    }
}
