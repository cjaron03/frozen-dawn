package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectFloodPolicyTest {
    @Test
    void proximityRunsFromEdgeToMelee() {
        assertEquals(0.0F,
                MasterArchitectFloodPolicy.proximity(15.0D), 0.0001F);
        assertEquals(1.0F,
                MasterArchitectFloodPolicy.proximity(1.5D), 0.0001F);
        assertTrue(MasterArchitectFloodPolicy.proximity(8.0D) > 0.0F);
    }

    @Test
    void fullCongregationCreatesExtremelySlowMeleeWalk() {
        assertEquals(0.0D,
                MasterArchitectFloodPolicy.movementModifier(
                        MasterArchitectFloodPolicy.proximity(12.0D), 1.0F),
                0.0001D);
        assertEquals(-0.94D,
                MasterArchitectFloodPolicy.movementModifier(1.0F, 1.0F),
                0.0001D);
        assertTrue(MasterArchitectFloodPolicy.movementModifier(1.0F, 0.0F)
                > -0.94D);
    }

    @Test
    void congregationScalesPresentationAndMotes() {
        assertEquals(1.0F, MasterArchitectFloodPolicy.strength(6, 6), 0.0001F);
        assertEquals(0.5F, MasterArchitectFloodPolicy.strength(3, 6), 0.0001F);
        assertEquals(7, MasterArchitectFloodPolicy.moteCount(1.0F));
        assertEquals(2, MasterArchitectFloodPolicy.moteCount(0.0F));
        assertTrue(MasterArchitectFloodPolicy.overlayAlpha(1.0F, 1.0F)
                <= 0.68F);
    }

    @Test
    void memoryLockOnlyBlocksTheFinalFourBlocks() {
        assertTrue(MasterArchitectFloodPolicy.isInsideMemoryLock(3.9D));
        assertTrue(!MasterArchitectFloodPolicy.isInsideMemoryLock(4.0D));
    }

    @Test
    void rushDamageHasGraceAndThenRamps() {
        assertEquals(0.0F,
                MasterArchitectFloodPolicy.rushDamage(
                        MasterArchitectFloodPolicy.RUSH_GRACE_TICKS, 1.0F, 1.0F),
                0.0001F);
        assertTrue(MasterArchitectFloodPolicy.rushDamage(240, 1.0F, 1.0F)
                > MasterArchitectFloodPolicy.rushDamage(41, 1.0F, 1.0F));
        assertTrue(MasterArchitectFloodPolicy.rushDamage(1000, 1.0F, 1.0F) <= 3.25F);
        assertTrue(MasterArchitectFloodPolicy.rushDamage(120, 1.0F, 1.0F)
                > MasterArchitectFloodPolicy.rushDamage(120, 1.0F, 0.25F));
    }

    @Test
    void ivenStacksScaleDamageWithoutCreatingImmunity() {
        assertEquals(0.15F, MasterArchitectFloodPolicy.stackDamageMultiplier(0), 0.0001F);
        assertEquals(1.0F, MasterArchitectFloodPolicy.stackDamageMultiplier(5), 0.0001F);
        assertEquals(1.0F, MasterArchitectFloodPolicy.stackDamageMultiplier(99), 0.0001F);
    }

    @Test
    void thronePressureScalesByPresetAndExposure() {
        assertEquals(0.04F, MasterArchitectFloodPolicy.copyHealRate("cinematic"), 0.0001F);
        assertEquals(0.06F, MasterArchitectFloodPolicy.copyHealRate("default"), 0.0001F);
        assertEquals(0.08F, MasterArchitectFloodPolicy.copyHealRate("brutal"), 0.0001F);
        assertEquals(240, MasterArchitectFloodPolicy.stackDecayTicks(false));
        assertEquals(180, MasterArchitectFloodPolicy.stackDecayTicks(true));
        assertEquals(0.60F, MasterArchitectFloodPolicy.failedFoldHealthCap("brutal"), 0.0001F);
        assertEquals(1.30F, MasterArchitectFloodPolicy.exposureIntensity(2), 0.0001F);
    }

    @Test
    void throneHealingEscalatesByPresetAndPressure() {
        assertEquals(1, MasterArchitectFloodPolicy.healingTier(
                "default", 1799, 1800, 3600, 1200, 2400));
        assertEquals(2, MasterArchitectFloodPolicy.healingTier(
                "default", 1800, 1800, 3600, 1200, 2400));
        assertEquals(3, MasterArchitectFloodPolicy.healingTier(
                "brutal", 2400, 1800, 3600, 1200, 2400));
        assertEquals(1, MasterArchitectFloodPolicy.healingTier(
                "cinematic", 20000, 1800, 3600, 1200, 2400));
        assertEquals(2.0F, MasterArchitectFloodPolicy.healingTierMultiplier(
                "default", 2, 2.0F, 3.5F, 4.0F), 0.0001F);
        assertEquals(4.0F, MasterArchitectFloodPolicy.healingTierMultiplier(
                "brutal", 3, 2.0F, 3.5F, 4.0F), 0.0001F);
    }
}
