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
    void loadSharingTriggersOnceAtHalfHealthBeforeLastWall() {
        assertFalse(MasterArchitectCombatPolicy.shouldUseTether(51.0F, 100.0F, false));
        assertTrue(MasterArchitectCombatPolicy.shouldUseTether(50.0F, 100.0F, false));
        assertFalse(MasterArchitectCombatPolicy.shouldUseTether(50.0F, 100.0F, true));
        assertFalse(MasterArchitectCombatPolicy.shouldUseTether(0.0F, 100.0F, false));
    }

    @Test
    void scoreMovementsFollowTheFightSkeleton() {
        assertEquals(MasterArchitectMusicStage.KIT,
                MasterArchitectMusicStage.forCombatState(false, false));
        assertEquals(MasterArchitectMusicStage.TETHER,
                MasterArchitectMusicStage.forCombatState(true, false));
        assertEquals(MasterArchitectMusicStage.LAST_WALL,
                MasterArchitectMusicStage.forCombatState(true, true));
        assertEquals(MasterArchitectMusicStage.LAST_WALL,
                MasterArchitectMusicStage.forCombatState(false, true));
        assertEquals(MasterArchitectMusicStage.OFF,
                MasterArchitectMusicStage.fromId(999));
    }

    @Test
    void loadSharingMakesNinetyPercentEligibleWithoutMakingTheMasterImmune() {
        assertEquals(36.0F, MasterArchitectCombatPolicy.desiredTetherRedirect(40.0F));
        assertEquals(0.0F, MasterArchitectCombatPolicy.desiredTetherRedirect(-1.0F));
        assertEquals(4.0F,
                40.0F - MasterArchitectCombatPolicy.desiredTetherRedirect(40.0F),
                0.0001F);
    }

    @Test
    void tetherFeedbackTracksChargeAndActualBreakthrough() {
        assertEquals(MasterArchitectCombatPolicy.TetherFeedbackState.HEALTHY,
                MasterArchitectCombatPolicy.tetherFeedbackState(9.0F, 9.0F, 0.75F));
        assertEquals(MasterArchitectCombatPolicy.TetherFeedbackState.STRAINED,
                MasterArchitectCombatPolicy.tetherFeedbackState(9.0F, 9.0F, 0.30F));
        assertEquals(MasterArchitectCombatPolicy.TetherFeedbackState.BREAKTHROUGH,
                MasterArchitectCombatPolicy.tetherFeedbackState(9.0F, 4.0F, 0.90F));
    }

    @Test
    void tetherChargeRechargesButNeverExceedsItsCap() {
        assertEquals(0.15F, MasterArchitectCombatPolicy.rechargeTether(0.0F), 0.0001F);
        assertEquals(MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER,
                MasterArchitectCombatPolicy.rechargeTether(
                        MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER),
                0.0001F);
    }

    @Test
    void transferCannotConsumeMoreChargeOrKillItsNode() {
        assertEquals(4.0F,
                MasterArchitectCombatPolicy.safeTransferRequest(8.0F, 4.0F, 20.0F));
        assertEquals(2.0F,
                MasterArchitectCombatPolicy.safeTransferRequest(8.0F, 12.0F, 3.0F));
        assertEquals(0.0F,
                MasterArchitectCombatPolicy.safeTransferRequest(8.0F, 12.0F, 1.0F));
    }

    @Test
    void lastWallCanDeliverItsFullPresetScaledHeal() {
        assertEquals(7.5F, MasterArchitectCombatPolicy.lastWallHealPerPulse(300.0F));
        assertEquals(5.0F, MasterArchitectCombatPolicy.lastWallHealPerPulse(200.0F));
        assertEquals(11.25F, MasterArchitectCombatPolicy.lastWallHealPerPulse(450.0F));
    }

    @Test
    void incomingBurstDamageIsCappedAfterFireVulnerability() {
        assertEquals(20.0F, MasterArchitectCombatPolicy.adjustedIncomingDamage(
                20.0F, false, false));
        assertEquals(30.0F, MasterArchitectCombatPolicy.adjustedIncomingDamage(
                20.0F, true, false));
        assertEquals(40.0F, MasterArchitectCombatPolicy.adjustedIncomingDamage(
                30.0F, true, false));
        assertEquals(40.0F, MasterArchitectCombatPolicy.adjustedIncomingDamage(
                100.0F, false, false));
        assertEquals(150.0F, MasterArchitectCombatPolicy.adjustedIncomingDamage(
                100.0F, true, true));
    }

    @Test
    void thermalSeverHasOneSecondGraceAndFourPulses() {
        assertEquals(3.0F, MasterArchitectCombatPolicy.THERMAL_PULSE_DAMAGE);
        assertEquals(0, MasterArchitectCombatPolicy.thermalPulseCountAt(19));
        assertEquals(1, MasterArchitectCombatPolicy.thermalPulseCountAt(20));
        assertEquals(2, MasterArchitectCombatPolicy.thermalPulseCountAt(40));
        assertEquals(3, MasterArchitectCombatPolicy.thermalPulseCountAt(60));
        assertEquals(4, MasterArchitectCombatPolicy.thermalPulseCountAt(80));
        assertEquals(4, MasterArchitectCombatPolicy.thermalPulseCountAt(100));
    }

    @Test
    void thermalSeverPulsesYieldToCoverOrHeat() {
        assertFalse(MasterArchitectCombatPolicy.shouldCancelThermalPulses(true, false));
        assertTrue(MasterArchitectCombatPolicy.shouldCancelThermalPulses(false, false));
        assertTrue(MasterArchitectCombatPolicy.shouldCancelThermalPulses(true, true));
    }

    @Test
    void thermalArmShowsCooldownAndCastReadiness() {
        assertEquals(0.0F, MasterArchitectCombatPolicy.thermalCooldownCharge(100, 100));
        assertEquals(0.5F, MasterArchitectCombatPolicy.thermalCooldownCharge(50, 100));
        assertEquals(1.0F, MasterArchitectCombatPolicy.thermalCooldownCharge(0, 100));
        assertEquals(0.0F, MasterArchitectCombatPolicy.thermalCastCharge(
                MasterArchitectCombatPolicy.THERMAL_CHARGE_START_TICK));
        assertEquals(1.0F, MasterArchitectCombatPolicy.thermalCastCharge(
                MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK));
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
    void deathChargeBuildsIntoAFullDetonationShake() {
        assertEquals(0.0F, MasterArchitectCombatPolicy.deathChargeProgress(3));
        assertEquals(0.0F, MasterArchitectCombatPolicy.deathShakeStrength(3));
        assertTrue(MasterArchitectCombatPolicy.deathChargeProgress(40) > 0.5F);
        assertTrue(MasterArchitectCombatPolicy.deathShakeStrength(40) > 0.4F);
        assertEquals(1.0F, MasterArchitectCombatPolicy.deathChargeProgress(60));
        assertTrue(MasterArchitectCombatPolicy.deathShakeStrength(70) > 1.2F);
    }

    @Test
    void deathBlastIsStrongestAtTheCenterAndCappedAtTheEdge() {
        assertEquals(6.0F, MasterArchitectCombatPolicy.deathBlastDamage(0.0D));
        assertEquals(4.0F, MasterArchitectCombatPolicy.deathBlastDamage(2.5D));
        assertEquals(2.0F, MasterArchitectCombatPolicy.deathBlastDamage(5.0D));
        assertEquals(2.0F, MasterArchitectCombatPolicy.deathBlastDamage(8.0D));
    }
}
