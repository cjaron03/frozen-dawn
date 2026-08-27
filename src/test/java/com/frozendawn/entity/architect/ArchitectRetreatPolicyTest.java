package com.frozendawn.entity.architect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectRetreatPolicyTest {

    @Test
    void pursuitCannotExtendRetreatPastCommittedTravelBudget() {
        assertEquals(
                ArchitectRetreatPolicy.RunEndReason.CONTINUE,
                ArchitectRetreatPolicy.runEndReason(2.0D, 11.99D, 50)
        );
        assertEquals(
                ArchitectRetreatPolicy.RunEndReason.TRAVEL_BUDGET,
                ArchitectRetreatPolicy.runEndReason(2.0D, 12.0D, 50)
        );
    }

    @Test
    void safeGapStillEndsRetreatEarly() {
        assertEquals(
                ArchitectRetreatPolicy.RunEndReason.SAFE_GAP,
                ArchitectRetreatPolicy.runEndReason(16.0D, 4.0D, 20)
        );
    }

    @Test
    void pathingTimeoutEndsAStalledRetreat() {
        assertEquals(
                ArchitectRetreatPolicy.RunEndReason.PATHING_TIMEOUT,
                ArchitectRetreatPolicy.runEndReason(2.0D, 1.0D, 100)
        );
    }
}
