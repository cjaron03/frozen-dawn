package com.frozendawn.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrostwrithePolicyTest {
    @Test
    void mimicryDetectsHighImpactCreaturesAtCanaryRange() {
        assertEquals(80.0D, FrostwrithePolicy.MIMIC_RADIUS, 0.0001D);
    }

    @Test
    void passivePatrolPrefersForwardUnvisitedGroundAwayFromColonies() {
        double preferred = FrostwrithePolicy.patrolScore(
                1.0D, 15.0D, 12.0D, 24.0D, 0.0D);
        double backtrack = FrostwrithePolicy.patrolScore(
                -1.0D, 15.0D, 1.0D, 3.0D, 0.0D);
        assertTrue(preferred > backtrack);
        assertEquals(8, FrostwrithePolicy.PATROL_MEMORY_SIZE);
    }

    private static final long DAY = 24_000L;

    @Test
    void evolutionCurvePreservesOrdinaryFrostmitesBeforeDayOne() {
        assertEquals(0.0F, FrostwrithePolicy.baseEvolutionChance(DAY - 1L), 0.0001F);
        assertEquals(0.05F, FrostwrithePolicy.baseEvolutionChance(DAY), 0.0001F);
        assertEquals(0.12F, FrostwrithePolicy.baseEvolutionChance(3L * DAY), 0.0001F);
        assertEquals(0.22F, FrostwrithePolicy.baseEvolutionChance(7L * DAY), 0.0001F);
    }

    @Test
    void bloomAndInfestedBonusesUseTheirLockedCaps() {
        assertEquals(0.30F, FrostwrithePolicy.evolutionChance(
                7L * DAY, 2.25F, 1.0D, false), 0.0001F);
        assertEquals(0.35F, FrostwrithePolicy.evolutionChance(
                7L * DAY, 2.25F, 1.0D, true), 0.0001F);
        assertEquals(0.22F, FrostwrithePolicy.evolutionChance(
                7L * DAY, 1.0F, 1.0D, false), 0.0001F);
    }

    @Test
    void damageClassificationRewardsAreaAndFireCounterplay() {
        assertEquals(6.0F, FrostwrithePolicy.cohesionDamage(
                6.0F, false, false, false, false), 0.0001F);
        assertEquals(12.0F, FrostwrithePolicy.cohesionDamage(
                6.0F, false, true, false, false), 0.0001F);
        assertEquals(18.0F, FrostwrithePolicy.cohesionDamage(
                6.0F, true, false, false, false), 0.0001F);
        assertEquals(6.0F, FrostwrithePolicy.cohesionDamage(
                6.0F, false, false, false, true), 0.0001F);
    }

    @Test
    void cohesionBandsRemoveRecognizableRepresentatives() {
        assertEquals(10, FrostwrithePolicy.visibleBodies(100.0F));
        assertEquals(7, FrostwrithePolicy.visibleBodies(69.9F));
        assertEquals(4, FrostwrithePolicy.visibleBodies(39.9F));
        assertEquals(2, FrostwrithePolicy.visibleBodies(14.9F));
    }

    @Test
    void regroupNeedsFourMitesFortyPercentBiomassAndNoRepellent() {
        assertTrue(FrostwrithePolicy.mayReassemble(4, 40, true, false));
        assertFalse(FrostwrithePolicy.mayReassemble(3, 100, true, false));
        assertFalse(FrostwrithePolicy.mayReassemble(10, 39, true, false));
        assertFalse(FrostwrithePolicy.mayReassemble(10, 100, false, false));
        assertFalse(FrostwrithePolicy.mayReassemble(10, 100, true, true));
    }

    @Test
    void biomassDivisionIsExactAndDeterministic() {
        int[] shares = new int[7];
        for (int index = 0; index < shares.length; index++) {
            shares[index] = FrostwrithePolicy.splitBiomass(53, shares.length, index);
        }
        assertEquals(53, Arrays.stream(shares).sum());
        assertEquals(8, shares[0]);
        assertEquals(7, shares[6]);
        assertEquals(5, FrostwrithePolicy.representativeCount(50));
    }

    @Test
    void unsafeTransitionStatesNormalizeOnReload() {
        assertTrue(FrostwritheState.CLIMBER.isUnsafeAfterReload());
        assertTrue(FrostwritheState.BURROWING.isUnsafeAfterReload());
        assertTrue(FrostwritheState.ERUPTING.isUnsafeAfterReload());
        assertTrue(FrostwritheState.DISASSEMBLING.isUnsafeAfterReload());
        assertFalse(FrostwritheState.CRAWLER.isUnsafeAfterReload());
        assertFalse(FrostwritheState.SHELL.isUnsafeAfterReload());
    }

    @Test
    void mimicPitchKeepsParrotVariationInAnUnnaturallyHighRegister() {
        assertEquals(1.65F, FrostwrithePolicy.mimicPitch(0.5F, 0.5F), 0.0001F);
        assertEquals(1.85F, FrostwrithePolicy.mimicPitch(1.0F, 0.0F), 0.0001F);
        assertEquals(1.45F, FrostwrithePolicy.mimicPitch(0.0F, 1.0F), 0.0001F);
    }

    @Test
    void ambientClusterFormationUsesTheConfiguredFortyPercentRoll() {
        assertTrue(FrostwrithePolicy.ambientClusterForms(0.0F));
        assertTrue(FrostwrithePolicy.ambientClusterForms(0.3999F));
        assertFalse(FrostwrithePolicy.ambientClusterForms(0.40F));
        assertFalse(FrostwrithePolicy.ambientClusterForms(0.99F));
        assertEquals(6, FrostwrithePolicy.AMBIENT_CLUSTER_MIN_MITES);
        assertEquals(400, FrostwrithePolicy.AMBIENT_CLUSTER_DWELL_TICKS);
    }

    @Test
    void blockedAssemblyUsesBoundedRetryAndWarningIntervals() {
        assertFalse(FrostwrithePolicy.mayRetryAssembly(1_059L, 1_060L));
        assertTrue(FrostwrithePolicy.mayRetryAssembly(1_060L, 1_060L));
        assertTrue(FrostwrithePolicy.shouldLogAssemblyFailure(1_000L, -1L));
        assertFalse(FrostwrithePolicy.shouldLogAssemblyFailure(1_199L, 1_000L));
        assertTrue(FrostwrithePolicy.shouldLogAssemblyFailure(1_200L, 1_000L));
        assertEquals(60, FrostwrithePolicy.ASSEMBLY_RETRY_TICKS);
        assertEquals(1_200, FrostwrithePolicy.AMBIENT_FAILURE_BACKOFF_TICKS);
    }
}
