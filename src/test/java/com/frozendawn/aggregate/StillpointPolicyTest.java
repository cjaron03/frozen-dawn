package com.frozendawn.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StillpointPolicyTest {
    @Test
    void coreSurvivesExactlyTwoRelocations() {
        assertEquals(1, StillpointPolicy.coreUsesAfterBreak(0));
        assertEquals(2, StillpointPolicy.coreUsesAfterBreak(1));
        assertFalse(StillpointPolicy.isFinalCoreBreak(0));
        assertFalse(StillpointPolicy.isFinalCoreBreak(1));
    }

    @Test
    void thirdRelocationExhaustsTheCore() {
        assertTrue(StillpointPolicy.isFinalCoreBreak(2));
        assertEquals(3, StillpointPolicy.coreUsesAfterBreak(2));
        assertEquals(3, StillpointPolicy.coreUsesAfterBreak(99));
    }
}
