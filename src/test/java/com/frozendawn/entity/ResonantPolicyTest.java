package com.frozendawn.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResonantPolicyTest {
    private static final long DAY = 24_000L;

    @Test
    void evolutionCurveKeepsOrdinaryHollowsBeforeDayOne() {
        assertEquals(0.0F, ResonantPolicy.baseEvolutionChance(DAY - 1L), 0.0001F);
        assertEquals(0.04F, ResonantPolicy.baseEvolutionChance(DAY), 0.0001F);
        assertEquals(0.04F, ResonantPolicy.baseEvolutionChance(3L * DAY - 1L), 0.0001F);
        assertEquals(0.10F, ResonantPolicy.baseEvolutionChance(3L * DAY), 0.0001F);
        assertEquals(0.18F, ResonantPolicy.baseEvolutionChance(7L * DAY), 0.0001F);
    }

    @Test
    void bloomAddsSevenPointsAndTotalCapsAtTwentyFivePercent() {
        assertEquals(0.25F, ResonantPolicy.evolutionChance(
                7L * DAY, 2.25F, 1.0D), 0.0001F);
        assertEquals(0.25F, ResonantPolicy.evolutionChance(
                7L * DAY, 20.0F, 4.0D), 0.0001F);
        assertEquals(0.18F, ResonantPolicy.evolutionChance(
                7L * DAY, 1.0F, 1.0D), 0.0001F);
    }

    @Test
    void sneakingAttenuatesMovementWithoutSilencingIt() {
        assertEquals(1.0F, ResonantPolicy.movementStrength(false, false), 0.0001F);
        assertEquals(0.2F, ResonantPolicy.movementStrength(false, true), 0.0001F);
        assertEquals(3.0F, ResonantPolicy.movementStrength(true, false), 0.0001F);
        assertEquals(0.6F, ResonantPolicy.movementStrength(true, true), 0.0001F);
    }

    @Test
    void repeatedSignalsReceiveTheLockedEightPointBonus() {
        float ordinary = ResonantPolicy.signalConfidence(5.0F, 12.0D, 4, false);
        float repeated = ResonantPolicy.signalConfidence(5.0F, 12.0D, 4, true);
        assertEquals(8.0F, repeated - ordinary, 0.0001F);
    }

    @Test
    void silenceDecaysAtTwoPointFivePerSecond() {
        assertEquals(47.5F, ResonantPolicy.decayConfidence(50.0F, 20), 0.0001F);
        assertEquals(0.0F, ResonantPolicy.decayConfidence(1.0F, 20), 0.0001F);
    }

    @Test
    void pulseOnlyFindsMovingPlayers() {
        assertTrue(ResonantPolicy.pulseReveals(true));
        assertFalse(ResonantPolicy.pulseReveals(false));
    }

    @Test
    void denseStructuresExtendSensingAndUnsafeStatesAreExplicit() {
        assertEquals(32, ResonantPolicy.sensingRadius(false));
        assertEquals(48, ResonantPolicy.sensingRadius(true));
        assertTrue(ResonantState.PHASING.isUnsafeAfterReload());
        assertTrue(ResonantState.BREACHING.isUnsafeAfterReload());
        assertTrue(ResonantState.GRABBING.isUnsafeAfterReload());
        assertFalse(ResonantState.LISTENING.isUnsafeAfterReload());
    }
}
