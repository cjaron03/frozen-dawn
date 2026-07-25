package com.frozendawn.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SuitIntegrityPolicyTest {

    @Test
    void sourceChancesUseConfiguredValues() {
        assertEquals(0.45F, chance(SuitIntegrityPolicy.SourceKind.MASTER_ARCHITECT));
        assertEquals(0.33F, chance(SuitIntegrityPolicy.SourceKind.ARCHITECT_HEAVY));
        assertEquals(0.65F, chance(SuitIntegrityPolicy.SourceKind.MIMIC_AMBUSH));
        assertEquals(0.20F, chance(SuitIntegrityPolicy.SourceKind.ORDINARY_PHYSICAL));
    }

    @Test
    void fallChanceScalesAndCapsAtSixtyPercent() {
        assertEquals(0.30F, chance(SuitIntegrityPolicy.SourceKind.FALL, 10.0F), 0.0001F);
        assertEquals(0.60F, chance(SuitIntegrityPolicy.SourceKind.FALL, 100.0F), 0.0001F);
    }

    @Test
    void graceAndConcurrentCapBothBlockPunctures() {
        assertFalse(SuitIntegrityPolicy.canPuncture(0, 1, 2));
        assertFalse(SuitIntegrityPolicy.canPuncture(2, 0, 2));
        assertTrue(SuitIntegrityPolicy.canPuncture(1, 0, 2));
    }

    @Test
    void ventRateScalesWithCapacityAndPunctureCount() {
        assertEquals(0.75D, SuitIntegrityPolicy.ventPerTick(1200, 80, 1), 0.0001D);
        assertEquals(4.50D, SuitIntegrityPolicy.ventPerTick(3600, 80, 2), 0.0001D);
        assertEquals(0.0D, SuitIntegrityPolicy.ventPerTick(3600, 80, 0), 0.0001D);
    }

    private static float chance(SuitIntegrityPolicy.SourceKind source) {
        return chance(source, 0.0F);
    }

    private static float chance(SuitIntegrityPolicy.SourceKind source, float fallDistance) {
        return SuitIntegrityPolicy.chance(
                source, fallDistance, 0.45F, 0.33F, 0.65F, 0.20F, 0.03F);
    }
}
