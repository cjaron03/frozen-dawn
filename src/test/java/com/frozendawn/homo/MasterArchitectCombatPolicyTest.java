package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectCombatPolicyTest {

    @Test
    void castsRequireRangeSightAndReadyCooldown() {
        double range = MasterArchitectCombatPolicy.CONTINUITY_RANGE;
        assertTrue(MasterArchitectCombatPolicy.canCast(
                range * range, range, true, 0));
        assertFalse(MasterArchitectCombatPolicy.canCast(
                range * range + 0.01D, range, true, 0));
        assertFalse(MasterArchitectCombatPolicy.canCast(
                range * range, range, false, 0));
        assertFalse(MasterArchitectCombatPolicy.canCast(
                range * range, range, true, 1));
    }

    @Test
    void lastWallIsOneUseAndTriggersAtThirtyPercent() {
        assertFalse(MasterArchitectCombatPolicy.shouldUseLastWall(31.0F, 100.0F, false));
        assertTrue(MasterArchitectCombatPolicy.shouldUseLastWall(30.0F, 100.0F, false));
        assertFalse(MasterArchitectCombatPolicy.shouldUseLastWall(30.0F, 100.0F, true));
        assertFalse(MasterArchitectCombatPolicy.shouldUseLastWall(0.0F, 100.0F, false));
    }

    @Test
    void thermalSeverHasOneSecondGraceAndFourPulses() {
        assertEquals(0, MasterArchitectCombatPolicy.thermalPulseCountAt(19));
        assertEquals(1, MasterArchitectCombatPolicy.thermalPulseCountAt(20));
        assertEquals(2, MasterArchitectCombatPolicy.thermalPulseCountAt(40));
        assertEquals(3, MasterArchitectCombatPolicy.thermalPulseCountAt(60));
        assertEquals(4, MasterArchitectCombatPolicy.thermalPulseCountAt(80));
        assertEquals(4, MasterArchitectCombatPolicy.thermalPulseCountAt(100));
    }

    @Test
    void thermalSeverSlowsHardThenRecoversTemperatureGradually() {
        assertEquals(3, MasterArchitectCombatPolicy.thermalSlownessAmplifierAt(0));
        assertEquals(3, MasterArchitectCombatPolicy.thermalSlownessAmplifierAt(39));
        assertEquals(2, MasterArchitectCombatPolicy.thermalSlownessAmplifierAt(40));
        assertEquals(2, MasterArchitectCombatPolicy.thermalSlownessAmplifierAt(99));
        assertEquals(-1, MasterArchitectCombatPolicy.thermalSlownessAmplifierAt(100));

        assertEquals(-220.0F,
                MasterArchitectCombatPolicy.adjustedTemperature(-180.0F, 50));
        assertEquals(-220.0F,
                MasterArchitectCombatPolicy.adjustedTemperature(-180.0F, 100));
        assertEquals(-200.0F,
                MasterArchitectCombatPolicy.adjustedTemperature(-180.0F, 130));
        assertEquals(-180.0F,
                MasterArchitectCombatPolicy.adjustedTemperature(-180.0F, 160));
    }

    @Test
    void stormMaintenanceOnlyUsesSafeCombatDowntime() {
        double outsideStaffRange = MasterArchitectCombatPolicy.STAFF_RANGE + 0.1D;
        assertTrue(MasterArchitectCombatPolicy.shouldMaintainStorm(
                outsideStaffRange * outsideStaffRange, 21, 0));
        assertFalse(MasterArchitectCombatPolicy.shouldMaintainStorm(
                outsideStaffRange * outsideStaffRange, 20, 0));
        assertFalse(MasterArchitectCombatPolicy.shouldMaintainStorm(
                outsideStaffRange * outsideStaffRange, 21, 1));
        assertFalse(MasterArchitectCombatPolicy.shouldMaintainStorm(
                MasterArchitectCombatPolicy.STAFF_RANGE
                        * MasterArchitectCombatPolicy.STAFF_RANGE,
                21,
                0));
    }

    @Test
    void deathChargeShakesThenLocksBeforeDetonation() {
        assertEquals(0.0F, MasterArchitectCombatPolicy.deathChargeProgress(13));
        assertEquals(0.0F, MasterArchitectCombatPolicy.deathShakeStrength(13));
        assertTrue(MasterArchitectCombatPolicy.deathChargeProgress(40) > 0.5F);
        assertTrue(MasterArchitectCombatPolicy.deathShakeStrength(40) > 0.5F);
        assertEquals(1.0F, MasterArchitectCombatPolicy.deathChargeProgress(60));
        assertEquals(0.0F, MasterArchitectCombatPolicy.deathShakeStrength(70));
    }

    @Test
    void deathBlastIsStrongestAtTheCenterAndCappedAtTheEdge() {
        assertEquals(6.0F, MasterArchitectCombatPolicy.deathBlastDamage(0.0D));
        assertEquals(4.0F, MasterArchitectCombatPolicy.deathBlastDamage(2.5D));
        assertEquals(2.0F, MasterArchitectCombatPolicy.deathBlastDamage(5.0D));
        assertEquals(2.0F, MasterArchitectCombatPolicy.deathBlastDamage(8.0D));
    }
}
