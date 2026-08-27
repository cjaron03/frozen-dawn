package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMaeveMoonPolicyTest {
    private static final long SEED = 0x4D4F4F4E5F544553L;

    @Test
    void firstRiseIsScheduledForTheNextDusk() {
        assertEquals(12_000L, PostMaeveMoonPolicy.nextDusk(0L));
        assertEquals(12_000L, PostMaeveMoonPolicy.nextDusk(11_999L));
        assertEquals(36_000L, PostMaeveMoonPolicy.nextDusk(12_000L));
        assertEquals(36_000L, PostMaeveMoonPolicy.nextDusk(30_000L));
    }

    @Test
    void firstRiseLastsExactlyTwelveThousandTicks() {
        PostMaeveMoonPolicy.Snapshot start = PostMaeveMoonPolicy.snapshot(0L, SEED);
        PostMaeveMoonPolicy.Snapshot finalRiseTick = PostMaeveMoonPolicy.snapshot(
                PostMaeveMoonPolicy.FIRST_RISE_TICKS - 1L, SEED);
        PostMaeveMoonPolicy.Snapshot completed = PostMaeveMoonPolicy.snapshot(
                PostMaeveMoonPolicy.FIRST_RISE_TICKS, SEED);

        assertEquals(PostMaeveMoonStage.FIRST_RISE, start.stage());
        assertEquals(PostMaeveMoonPolicy.FIRST_RISE_START_ELEVATION,
                start.elevationDegrees(), 0.001F);
        assertEquals(PostMaeveMoonStage.FIRST_RISE, finalRiseTick.stage());
        assertEquals(PostMaeveMoonStage.STRESSED, completed.stage());
        assertEquals(0, start.debrisCount());
    }

    @Test
    void damageMilestonesOccurAtOneFiveAndFifteenDays() {
        long rise = PostMaeveMoonPolicy.FIRST_RISE_TICKS;
        assertEquals(PostMaeveMoonStage.STRESSED,
                PostMaeveMoonPolicy.snapshot(
                        rise + PostMaeveMoonPolicy.CALVING_AGE_TICKS - 1L,
                        SEED).stage());
        assertEquals(PostMaeveMoonStage.CALVING,
                PostMaeveMoonPolicy.snapshot(
                        rise + PostMaeveMoonPolicy.CALVING_AGE_TICKS,
                        SEED).stage());
        assertEquals(PostMaeveMoonStage.RAGGED,
                PostMaeveMoonPolicy.snapshot(
                        rise + PostMaeveMoonPolicy.RAGGED_AGE_TICKS,
                        SEED).stage());
        assertEquals(PostMaeveMoonStage.RINGING,
                PostMaeveMoonPolicy.snapshot(
                        rise + PostMaeveMoonPolicy.RING_AGE_TICKS,
                        SEED).stage());
    }

    @Test
    void orbitRepeatsEveryNinetySixDays() {
        long rise = PostMaeveMoonPolicy.FIRST_RISE_TICKS;
        PostMaeveMoonPolicy.Snapshot first = PostMaeveMoonPolicy.snapshot(
                rise + 17_333L, SEED);
        PostMaeveMoonPolicy.Snapshot repeated = PostMaeveMoonPolicy.snapshot(
                rise + 17_333L + PostMaeveMoonPolicy.ORBIT_PERIOD_TICKS, SEED);

        assertEquals(first.azimuthDegrees(), repeated.azimuthDegrees(), 0.001F);
        assertEquals(first.elevationDegrees(), repeated.elevationDegrees(), 0.001F);
        assertEquals(first.illuminationAngleDegrees(),
                repeated.illuminationAngleDegrees(), 0.001F);
    }

    @Test
    void rollbackNeverSubtractsTimelineAge() {
        assertEquals(40L, PostMaeveMoonPolicy.positiveDayTimeAdvance(100L, 140L));
        assertEquals(0L, PostMaeveMoonPolicy.positiveDayTimeAdvance(140L, 100L));
        assertEquals(0L, PostMaeveMoonPolicy.positiveDayTimeAdvance(140L, 140L));
    }

    @Test
    void lateDamageNeverDestroysThePrimaryMoon() {
        long veryLate = PostMaeveMoonPolicy.FIRST_RISE_TICKS
                + 2_000L * PostMaeveMoonPolicy.DAY_TICKS;
        PostMaeveMoonPolicy.Snapshot snapshot = PostMaeveMoonPolicy.snapshot(
                veryLate, SEED);

        assertTrue(snapshot.discIntegrity()
                >= PostMaeveMoonPolicy.MINIMUM_DISC_INTEGRITY);
        assertTrue(snapshot.fractureProgress() < 1.0F);
        assertEquals(72, snapshot.debrisCount());
    }

    @Test
    void apparentSizeReachesItsFinalScaleOnTheLastAgingDay() {
        assertEquals(1.0F, PostMaeveMoonPolicy.apparentSizeScale(0L), 0.0001F);
        assertEquals(1.25F, PostMaeveMoonPolicy.apparentSizeScale(
                5L * PostMaeveMoonPolicy.DAY_TICKS), 0.0001F);
        assertEquals(PostMaeveMoonPolicy.MAXIMUM_APPARENT_SIZE_SCALE,
                PostMaeveMoonPolicy.apparentSizeScale(
                PostMaeveMoonPolicy.RING_AGE_TICKS), 0.0001F);
        assertEquals(PostMaeveMoonPolicy.MAXIMUM_APPARENT_SIZE_SCALE,
                PostMaeveMoonPolicy.apparentSizeScale(
                        5_000L * PostMaeveMoonPolicy.DAY_TICKS),
                0.0001F);
    }

    @Test
    void brokenOrbitVariesSpeedWithoutReversing() {
        float previous = PostMaeveMoonPolicy.brokenOrbitDegrees(0.0D, SEED);
        float minimumStep = Float.MAX_VALUE;
        float maximumStep = Float.MIN_VALUE;
        for (int tick = 200; tick <= PostMaeveMoonPolicy.DAY_TICKS; tick += 200) {
            float current = PostMaeveMoonPolicy.brokenOrbitDegrees(tick, SEED);
            float step = Math.floorMod(
                    Math.round((current - previous) * 1_000.0F), 360_000)
                    / 1_000.0F;
            assertTrue(step > 0.0F);
            minimumStep = Math.min(minimumStep, step);
            maximumStep = Math.max(maximumStep, step);
            previous = current;
        }
        assertTrue(maximumStep > minimumStep * 1.5F);
    }
}
