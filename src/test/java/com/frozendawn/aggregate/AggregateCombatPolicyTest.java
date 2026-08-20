package com.frozendawn.aggregate;

import com.frozendawn.config.ConfigPresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AggregateCombatPolicyTest {
    @Test
    void presetHealthAndOverfeedRemainCapped() {
        assertEquals(500.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.CINEMATIC, 0.0D));
        assertEquals(700.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.DEFAULT, 0.0D));
        assertEquals(1_000.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.BRUTAL, 0.0D));
        assertEquals(650.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.CINEMATIC, 99_000.0D));
        assertEquals(900.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.DEFAULT, 99_000.0D));
        assertEquals(1_300.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.BRUTAL, 99_000.0D));
    }

    @Test
    void participantScalingUsesLockedFivePlayerCap() {
        assertEquals(1.0F, AggregateCombatPolicy.participantMultiplier(1));
        assertEquals(1.4F, AggregateCombatPolicy.participantMultiplier(2));
        assertEquals(1.8F, AggregateCombatPolicy.participantMultiplier(3));
        assertEquals(2.2F, AggregateCombatPolicy.participantMultiplier(4));
        assertEquals(2.6F, AggregateCombatPolicy.participantMultiplier(5));
        assertEquals(2.6F, AggregateCombatPolicy.participantMultiplier(12));
    }

    @Test
    void healthFractionsSelectTheThreeCombatPhases() {
        assertEquals(AggregatePhase.COHERENT, AggregateCombatPolicy.phaseForFraction(0.71F));
        assertEquals(AggregatePhase.REALLOCATED, AggregateCombatPolicy.phaseForFraction(0.70F));
        assertEquals(AggregatePhase.CONVERGENCE_FAILURE,
                AggregateCombatPolicy.phaseForFraction(0.35F));
    }

    @Test
    void returningFragmentsHealTwoPercentWithTenPercentEventCap() {
        assertEquals(20.0F, AggregateCombatPolicy.fragmentReturnHeal(
                1_000.0F, 0.0F, AggregatePhase.COHERENT));
        assertEquals(5.0F, AggregateCombatPolicy.fragmentReturnHeal(
                1_000.0F, 95.0F, AggregatePhase.REALLOCATED));
        assertEquals(0.0F, AggregateCombatPolicy.fragmentReturnHeal(
                1_000.0F, 100.0F, AggregatePhase.COHERENT));
        assertEquals(0.0F, AggregateCombatPolicy.fragmentReturnHeal(
                1_000.0F, 0.0F, AggregatePhase.CONVERGENCE_FAILURE));
    }

    @Test
    void undoneDominanceMakesBankedOverfeedDenserButStillUsesTheCap() {
        assertEquals(100.0D, AggregateCombatPolicy.effectiveOverfeed(
                100.0D, AggregateLineage.RIMEBOUND));
        assertEquals(125.0D, AggregateCombatPolicy.effectiveOverfeed(
                100.0D, AggregateLineage.UNDONE));
        assertEquals(900.0F, AggregateCombatPolicy.awakenedHealth(
                ConfigPresets.DEFAULT, AggregateCombatPolicy.effectiveOverfeed(
                        100_000.0D, AggregateLineage.UNDONE)));
    }

    @Test
    void dominantBuildUpgradesTwoTraitsInsteadOfForcingAWeakThird() {
        assertEquals(1, AggregateCombatPolicy.activeTraitCount(
                AggregatePhase.COHERENT, 3, false));
        assertEquals(2, AggregateCombatPolicy.activeTraitCount(
                AggregatePhase.REALLOCATED, 3, false));
        assertEquals(3, AggregateCombatPolicy.activeTraitCount(
                AggregatePhase.CONVERGENCE_FAILURE, 3, false));
        assertEquals(2, AggregateCombatPolicy.activeTraitCount(
                AggregatePhase.CONVERGENCE_FAILURE, 3, true));
    }
}
