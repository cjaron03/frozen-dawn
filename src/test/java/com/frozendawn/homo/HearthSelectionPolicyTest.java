package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthSelectionPolicyTest {

    private static final BlockPos ANCHOR = new BlockPos(320, 68, -240);

    @Test
    void selectionGateBeginsFifteenMinutesIntoLatePhaseSix() {
        long latePhaseStart = 100L * 24000L * 85L / 100L;
        long eligibilityTick = latePhaseStart + HearthSelectionPolicy.LATE_PHASE_DELAY_TICKS;

        assertEquals(2_058_000L, HearthSelectionPolicy.selectionEligibilityTick(100));
        assertFalse(HearthSelectionPolicy.isSelectionEligible(eligibilityTick - 1L, 100));
        assertTrue(HearthSelectionPolicy.isSelectionEligible(eligibilityTick, 100));
        assertEquals(Long.MAX_VALUE, HearthSelectionPolicy.selectionEligibilityTick(0));
    }

    @Test
    void sameSeedAndAnchorProduceTheSamePlan() {
        HearthSelectionPolicy.SelectionPlan first = HearthSelectionPolicy.createPlan(8675309L, ANCHOR);
        HearthSelectionPolicy.SelectionPlan second = HearthSelectionPolicy.createPlan(8675309L, ANCHOR);

        assertEquals(first, second);
    }

    @Test
    void differentWorldSeedsProduceDifferentMajorSites() {
        HearthSelectionPolicy.SelectionPlan first = HearthSelectionPolicy.createPlan(12L, ANCHOR);
        HearthSelectionPolicy.SelectionPlan second = HearthSelectionPolicy.createPlan(13L, ANCHOR);

        assertNotEquals(first.major(), second.major());
    }

    @Test
    void majorSitesStayWithinTheirConfiguredRing() {
        for (long seed = 0L; seed < 500L; seed++) {
            BlockPos center = HearthSelectionPolicy.createPlan(seed, ANCHOR).major().center();
            double distance = horizontalDistance(ANCHOR, center);
            assertTrue(distance >= HearthSelectionPolicy.MAJOR_MIN_RADIUS - 1.0D);
            assertTrue(distance <= HearthSelectionPolicy.MAJOR_MAX_RADIUS + 1.0D);
            assertEquals(0, center.getY());
        }
    }

    @Test
    void optionalMinorIsNeitherGuaranteedNorImpossibleAndKeepsItsDistance() {
        int minorCount = 0;
        for (long seed = 0L; seed < 500L; seed++) {
            HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(seed, ANCHOR);
            if (plan.minor().isEmpty()) {
                continue;
            }

            minorCount++;
            BlockPos minor = plan.minor().orElseThrow().center();
            double anchorDistance = horizontalDistance(ANCHOR, minor);
            double majorDistance = horizontalDistance(plan.major().center(), minor);
            assertTrue(anchorDistance >= HearthSelectionPolicy.MINOR_MIN_RADIUS - 1.0D);
            assertTrue(anchorDistance <= HearthSelectionPolicy.MINOR_MAX_RADIUS + 1.0D);
            assertTrue(majorDistance >= HearthSelectionPolicy.MINIMUM_SITE_SEPARATION);
        }

        assertTrue(minorCount > 0);
        assertTrue(minorCount < 500);
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
