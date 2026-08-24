package com.frozendawn.event;

import com.frozendawn.data.SuitIntegrity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuitIntegrityPolicyTest {

    @Test
    void sourceChancesUseConfiguredValues() {
        assertEquals(0.06F, chance(SuitIntegrityPolicy.SourceKind.MASTER_ARCHITECT));
        assertEquals(0.12F, chance(SuitIntegrityPolicy.SourceKind.ARCHITECT_HEAVY));
        assertEquals(0.30F, chance(SuitIntegrityPolicy.SourceKind.MIMIC_AMBUSH));
        assertEquals(0.05F, chance(SuitIntegrityPolicy.SourceKind.ORDINARY_PHYSICAL));
    }

    @Test
    void fallChanceScalesAndCapsAtThirtyPercent() {
        assertEquals(0.10F, chance(SuitIntegrityPolicy.SourceKind.FALL, 10.0F), 0.0001F);
        assertEquals(0.30F, chance(SuitIntegrityPolicy.SourceKind.FALL, 100.0F), 0.0001F);
    }

    @Test
    void oneAttackerImpactCannotRollTwice() {
        SuitIntegrity state = new SuitIntegrity();
        UUID mimic = UUID.randomUUID();

        assertTrue(state.claimPunctureRoll(120L, mimic));
        assertFalse(state.claimPunctureRoll(120L, mimic));
        assertTrue(state.claimPunctureRoll(121L, mimic));
        assertTrue(state.claimPunctureRoll(121L, UUID.randomUUID()));
        assertTrue(state.claimPunctureRoll(121L, null));
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

    @Test
    void emergencyCartridgeRestoresThirtyFivePercentWithFixedCap() {
        assertEquals(420, SuitIntegrityPolicy.emergencyRefillAmount(1200));
        assertEquals(840, SuitIntegrityPolicy.emergencyRefillAmount(2400));
        assertEquals(1200, SuitIntegrityPolicy.emergencyRefillAmount(3600));
        assertEquals(1200, SuitIntegrityPolicy.emergencyRefillAmount(10800));
        assertEquals(0, SuitIntegrityPolicy.emergencyRefillAmount(0));
    }

    private static float chance(SuitIntegrityPolicy.SourceKind source) {
        return chance(source, 0.0F);
    }

    private static float chance(SuitIntegrityPolicy.SourceKind source, float fallDistance) {
        return SuitIntegrityPolicy.chance(
                source, fallDistance, 0.06F, 0.12F, 0.30F, 0.05F, 0.01F);
    }
}
