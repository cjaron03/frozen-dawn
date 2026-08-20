package com.frozendawn.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMaeveEncounterPolicyTest {
    @Test
    void successfulEncounterEnforcesItsMinimumInterval() {
        PostMaeveEncounterType type = PostMaeveEncounterType.RIMEBOUND;
        assertFalse(PostMaeveEncounterPolicy.typeCooldownReady(
                type, type.minimumIntervalTicks() - 1L, 0L));
        assertTrue(PostMaeveEncounterPolicy.typeCooldownReady(
                type, type.minimumIntervalTicks(), 0L));
    }

    @Test
    void pressureGuaranteesAnEncounterAtTheMaximumInterval() {
        PostMaeveEncounterType type = PostMaeveEncounterType.RESONANT;
        assertEquals(1.0D, PostMaeveEncounterPolicy.effectiveChance(
                type, 0.18D, type.guaranteedIntervalTicks(),
                0L, 0, false), 0.0001D);
    }

    @Test
    void failedAttemptsRaiseChanceWithoutExceedingTheCap() {
        long midpoint = (PostMaeveEncounterType.FROSTWRITHE.minimumIntervalTicks()
                + PostMaeveEncounterType.FROSTWRITHE.guaranteedIntervalTicks()) / 2L;
        double base = PostMaeveEncounterPolicy.effectiveChance(
                PostMaeveEncounterType.FROSTWRITHE, 0.12D,
                midpoint, 0L, 0, false);
        double pressured = PostMaeveEncounterPolicy.effectiveChance(
                PostMaeveEncounterType.FROSTWRITHE, 0.12D,
                midpoint, 0L, 6, false);
        assertEquals(0.56D, base, 0.0001D);
        assertEquals(0.635D, pressured, 0.0001D);
    }

    @Test
    void recentRepeatIsDampedUntilItsGuarantee() {
        double ordinary = PostMaeveEncounterPolicy.effectiveChance(
                PostMaeveEncounterType.RIMEBOUND, 0.30D,
                8_000L, 0L, 0, false);
        double repeated = PostMaeveEncounterPolicy.effectiveChance(
                PostMaeveEncounterType.RIMEBOUND, 0.30D,
                8_000L, 0L, 0, true);
        assertEquals(ordinary * PostMaeveEncounterPolicy.REPEAT_MULTIPLIER,
                repeated, 0.0001D);
    }

    @Test
    void sharedCooldownSeparatesDifferentEncounterTypes() {
        assertFalse(PostMaeveEncounterPolicy.globalCooldownReady(
                PostMaeveEncounterPolicy.GLOBAL_ENCOUNTER_COOLDOWN_TICKS - 1L, 0L));
        assertTrue(PostMaeveEncounterPolicy.globalCooldownReady(
                PostMaeveEncounterPolicy.GLOBAL_ENCOUNTER_COOLDOWN_TICKS, 0L));
    }
}
