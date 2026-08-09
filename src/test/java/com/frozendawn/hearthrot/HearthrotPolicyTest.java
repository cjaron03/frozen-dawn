package com.frozendawn.hearthrot;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthrotPolicyTest {
    private static final double EPSILON = 0.000001D;

    @Test
    void coreColonizationBecomesVisibleAtThirtyMinutesAndFillsAtTwoHours() {
        double rate = HearthrotPolicy.coreColonizationPerTick();
        assertEquals(HearthrotPolicy.FIRST_VISIBLE_COLONIZATION,
                rate * 30.0D * 60.0D * 20.0D, EPSILON);
        assertEquals(HearthrotPolicy.MAX_COLONIZATION,
                rate * 120.0D * 60.0D * 20.0D, EPSILON);
        assertFalse(HearthrotPolicy.isInfectable(2_499));
        assertTrue(HearthrotPolicy.isInfectable(2_500));
    }

    @Test
    void visualColonizationStagesUseTheFourLockedThresholds() {
        assertEquals(0, HearthrotPolicy.visualStage(2_499));
        assertEquals(1, HearthrotPolicy.visualStage(2_500));
        assertEquals(2, HearthrotPolicy.visualStage(5_000));
        assertEquals(3, HearthrotPolicy.visualStage(7_500));
        assertEquals(4, HearthrotPolicy.visualStage(10_000));
    }

    @Test
    void warmthCleansAFullRigAtTheLockedRates() {
        assertEquals(HearthrotPolicy.MAX_COLONIZATION,
                HearthrotPolicy.activeHeatCleaningPerTick()
                        * 20.0D * 60.0D * 20.0D, EPSILON);
        assertEquals(HearthrotPolicy.MAX_COLONIZATION,
                HearthrotPolicy.warmInteriorCleaningPerTick()
                        * 40.0D * 60.0D * 20.0D, EPSILON);
    }

    @Test
    void presetTimelinesTotalSixNineAndFourAndAHalfHours() {
        assertEquals(minutes(360), totalDuration(HearthrotPolicy.Preset.NORMAL));
        assertEquals(minutes(540), totalDuration(HearthrotPolicy.Preset.CINEMATIC));
        assertEquals(minutes(270), totalDuration(HearthrotPolicy.Preset.BRUTAL));
    }

    @Test
    void carefulWarmBloomFreeMovementPlateausNearThreePercent() {
        double carefulRate = HearthrotPolicy.progressionRate(
                -1, 18.0F, true, 0);
        assertTrue(carefulRate >= 0.03D);
        assertTrue(carefulRate <= 0.032D);
        assertTrue(HearthrotPolicy.progressionRate(
                2, -100.0F, false, 10_000) > 1.0D);
    }

    @Test
    void exteriorColonizationAndDiseaseApplyOnlyTheirLockedO2Costs() {
        assertEquals(1.08D, HearthrotPolicy.externalO2Multiplier(1), EPSILON);
        assertEquals(1.16D, HearthrotPolicy.externalO2Multiplier(2), EPSILON);
        assertEquals(1.24D, HearthrotPolicy.externalO2Multiplier(3), EPSILON);
        assertEquals(1.32D, HearthrotPolicy.externalO2Multiplier(4), EPSILON);
        assertEquals(1.15D, HearthrotPolicy.diseaseO2Multiplier(4), EPSILON);
        assertEquals(1.30D, HearthrotPolicy.diseaseO2Multiplier(5), EPSILON);
        assertEquals(1.50D, HearthrotPolicy.diseaseO2Multiplier(6), EPSILON);
    }

    @Test
    void exteriorColonizationShortensOnlyTemporarySealLifetime() {
        assertEquals(0.90D,
                HearthrotPolicy.temporarySealLifetimeMultiplier(1), EPSILON);
        assertEquals(0.80D,
                HearthrotPolicy.temporarySealLifetimeMultiplier(2), EPSILON);
        assertEquals(0.65D,
                HearthrotPolicy.temporarySealLifetimeMultiplier(3), EPSILON);
        assertEquals(0.50D,
                HearthrotPolicy.temporarySealLifetimeMultiplier(4), EPSILON);
    }

    @Test
    void lateStageCapabilityLossMatchesTheLockedTable() {
        assertEquals(3, HearthrotPolicy.maxHealthPenaltyHearts(4));
        assertEquals(5, HearthrotPolicy.maxHealthPenaltyHearts(6));
        assertEquals(15.0F, HearthrotPolicy.hiddenColdPenalty(4));
        assertEquals(40.0F, HearthrotPolicy.hiddenColdPenalty(6));
        assertEquals(-0.10D, HearthrotPolicy.movementPenalty(4, false), EPSILON);
        assertEquals(-0.20D, HearthrotPolicy.movementPenalty(4, true), EPSILON);
        assertEquals(-0.35D, HearthrotPolicy.movementPenalty(6, true), EPSILON);
        assertEquals(2, HearthrotPolicy.foodFreezeMultiplier(5));
        assertEquals(3, HearthrotPolicy.foodFreezeMultiplier(6));
    }

    @Test
    void crystallineHurtSoundsBeginAtCoughingStage() {
        assertFalse(HearthrotPolicy.usesCrystallineHurtSounds(2));
        assertTrue(HearthrotPolicy.usesCrystallineHurtSounds(3));
        assertTrue(HearthrotPolicy.usesCrystallineHurtSounds(6));
    }

    @Test
    void deathNeverCuresSilentHearthrot() {
        assertEquals(0, HearthrotPolicy.stageAfterDeath(0));
        assertEquals(1, HearthrotPolicy.stageAfterDeath(1));
        assertEquals(1, HearthrotPolicy.stageAfterDeath(2));
        assertEquals(5, HearthrotPolicy.stageAfterDeath(6));
    }

    @Test
    void salvationRollOccursOncePerQualifyingStillnessEpisode() {
        assertFalse(HearthrotPolicy.shouldRollSalvation(
                HearthrotPolicy.SALVATION_STILLNESS_TICKS - 1, false));
        assertTrue(HearthrotPolicy.shouldRollSalvation(
                HearthrotPolicy.SALVATION_STILLNESS_TICKS, false));
        assertFalse(HearthrotPolicy.shouldRollSalvation(
                HearthrotPolicy.SALVATION_STILLNESS_TICKS + 500, true));
    }

    @Test
    void coughAndWheezeBeginAtTheirLockedStages() {
        assertEquals(0, HearthrotPolicy.coughMinimumSeconds(2));
        assertEquals(75, HearthrotPolicy.coughMinimumSeconds(3));
        assertEquals(150, HearthrotPolicy.coughMaximumSeconds(3));
        assertEquals(45, HearthrotPolicy.coughMinimumSeconds(6));
        assertEquals(90, HearthrotPolicy.coughMaximumSeconds(6));

        assertEquals(0, HearthrotPolicy.wheezeMinimumSeconds(3));
        assertEquals(120, HearthrotPolicy.wheezeMinimumSeconds(4));
        assertEquals(260, HearthrotPolicy.wheezeMaximumSeconds(4));
        assertEquals(70, HearthrotPolicy.wheezeMinimumSeconds(6));
        assertEquals(180, HearthrotPolicy.wheezeMaximumSeconds(6));

        assertEquals(0, HearthrotPolicy.breathCatchMinimumSeconds(3));
        assertEquals(160, HearthrotPolicy.breathCatchMinimumSeconds(4));
        assertEquals(300, HearthrotPolicy.breathCatchMaximumSeconds(4));
        assertEquals(90, HearthrotPolicy.breathCatchMinimumSeconds(6));
        assertEquals(180, HearthrotPolicy.breathCatchMaximumSeconds(6));
    }

    private static int totalDuration(HearthrotPolicy.Preset preset) {
        return IntStream.range(1, 6)
                .map(stage -> HearthrotPolicy.stageDurationTicks(stage, preset))
                .sum();
    }

    private static int minutes(int minutes) {
        return minutes * 60 * 20;
    }
}
