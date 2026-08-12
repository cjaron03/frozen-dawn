package com.frozendawn.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RimeboundPolicyTest {
    private static final long DAY = 24_000L;

    @Test
    void evolutionCurveUsesLockedPostMaeveMilestones() {
        assertEquals(0.0F, RimeboundPolicy.baseEvolutionChance(DAY - 1L), 0.0001F);
        assertEquals(0.05F, RimeboundPolicy.baseEvolutionChance(DAY), 0.0001F);
        assertEquals(0.05F, RimeboundPolicy.baseEvolutionChance(3L * DAY - 1L), 0.0001F);
        assertEquals(0.12F, RimeboundPolicy.baseEvolutionChance(3L * DAY), 0.0001F);
        assertEquals(0.22F, RimeboundPolicy.baseEvolutionChance(7L * DAY), 0.0001F);
    }

    @Test
    void bloomAddsAtMostEightPointsAndTotalCapsAtThirtyPercent() {
        assertEquals(0.30F, RimeboundPolicy.evolutionChance(
                7L * DAY, 2.25F, 1.0D), 0.0001F);
        assertEquals(0.30F, RimeboundPolicy.evolutionChance(
                7L * DAY, 20.0F, 4.0D), 0.0001F);
        assertEquals(0.22F, RimeboundPolicy.evolutionChance(
                7L * DAY, 1.0F, 1.0D), 0.0001F);
    }

    @Test
    void shellDamageRewardsHeavyMeleeAndResistsProjectileChip() {
        assertEquals(3, RimeboundPolicy.shellDamage(6.0F, true, false));
        assertEquals(11, RimeboundPolicy.shellDamage(6.0F, false, true));
        assertEquals(6, RimeboundPolicy.shellDamage(6.0F, false, false));
    }

    @Test
    void unsafeReloadStatesAreExplicit() {
        assertEquals(true, RimeboundState.BURROWING.isUnsafeAfterReload());
        assertEquals(true, RimeboundState.ERUPTING.isUnsafeAfterReload());
        assertEquals(false, RimeboundState.RANGED_WINDUP.isUnsafeAfterReload());
    }

    @Test
    void encasementStagesHaveReadableThresholds() {
        assertEquals(0, RimeboundEncasement.stage(24.9F));
        assertEquals(1, RimeboundEncasement.stage(25.0F));
        assertEquals(2, RimeboundEncasement.stage(50.0F));
        assertEquals(3, RimeboundEncasement.stage(75.0F));
        assertEquals(4, RimeboundEncasement.stage(100.0F));
    }
}
