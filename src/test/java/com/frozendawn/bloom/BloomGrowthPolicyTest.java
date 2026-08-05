package com.frozendawn.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomGrowthPolicyTest {
    @Test
    void presetsReachEightHundredAtTheirLockedDurations() {
        assertEquals(800.0D, BloomGrowthPolicy.radius(
                30L * BloomGrowthPolicy.DAY_TICKS,
                30L * BloomGrowthPolicy.DAY_TICKS), 0.001D);
        assertEquals(800.0D, BloomGrowthPolicy.radius(
                60L * BloomGrowthPolicy.DAY_TICKS,
                60L * BloomGrowthPolicy.DAY_TICKS), 0.001D);
        assertEquals(800.0D, BloomGrowthPolicy.radius(
                15L * BloomGrowthPolicy.DAY_TICKS,
                15L * BloomGrowthPolicy.DAY_TICKS), 0.001D);
    }

    @Test
    void growthContinuesAtSameVelocityAndCapsAtOneThousand() {
        long duration = 30L * BloomGrowthPolicy.DAY_TICKS;
        double speed = (800.0D - 12.0D) / duration;
        long extraTicks = Math.round(200.0D / speed);
        assertEquals(1_000.0D,
                BloomGrowthPolicy.radius(duration + extraTicks, duration), 0.01D);
        assertEquals(1_000.0D,
                BloomGrowthPolicy.radius(Long.MAX_VALUE, duration), 0.0D);
    }

    @Test
    void overlapRaisesCoverageAndHeightWithinCaps() {
        long hash = BloomGrowthPolicy.mix(42L);
        double single = BloomGrowthPolicy.coverage(BloomBand.MID, hash, 1);
        double overlap = BloomGrowthPolicy.coverage(BloomBand.MID, hash, 2);
        assertEquals(0.25D, overlap - single, 0.0001D);
        assertTrue(BloomGrowthPolicy.coverage(BloomBand.CORE, hash, 8) <= 0.85D);
        assertEquals(30, BloomGrowthPolicy.maxHeight(BloomBand.CORE, hash, 8));
    }

    @Test
    void sealedWearMatchesDensityLifetimesAndNeverHeals() {
        assertEquals(0, BloomGrowthPolicy.sealedWearStage(0L, BloomBand.CORE));
        assertEquals(2, BloomGrowthPolicy.sealedWearStage(
                BloomGrowthPolicy.sealedLifetimeTicks(BloomBand.CORE) / 2L,
                BloomBand.CORE));
        assertEquals(4, BloomGrowthPolicy.sealedWearStage(
                BloomGrowthPolicy.sealedLifetimeTicks(BloomBand.FRONTIER),
                BloomBand.FRONTIER));
    }

    @Test
    void chunkSeedingIsDeterministicAndBounded() {
        long first = BloomGrowthPolicy.chunkSeed(1234L, 5678L, 9012L);
        assertEquals(first, BloomGrowthPolicy.chunkSeed(1234L, 5678L, 9012L));
        assertTrue(BloomGrowthPolicy.initialTipAttempts(first) >= 1);
        assertTrue(BloomGrowthPolicy.initialTipAttempts(first) <= 3);
        assertTrue(first != BloomGrowthPolicy.chunkSeed(1234L, 5678L, 9013L));
    }

    @Test
    void spentLatticeYieldMatchesDensityRules() {
        assertEquals(0, BloomGrowthPolicy.spentLatticeDrops(BloomBand.FRONTIER, 0.0F));
        assertEquals(1, BloomGrowthPolicy.spentLatticeDrops(BloomBand.MID, 0.24F));
        assertEquals(0, BloomGrowthPolicy.spentLatticeDrops(BloomBand.MID, 0.25F));
        assertEquals(2, BloomGrowthPolicy.spentLatticeDrops(BloomBand.CORE, 0.24F));
        assertEquals(1, BloomGrowthPolicy.spentLatticeDrops(BloomBand.CORE, 0.25F));
    }

    @Test
    void bloomDensityRaisesUndoneAndBloomboundSpawnChancesWithinCaps() {
        assertEquals(0.011D, BloomGrowthPolicy.undoneSpawnChance(0.011D, 0.0F),
                0.000001D);
        assertEquals(0.088D, BloomGrowthPolicy.undoneSpawnChance(0.011D, 1.0F),
                0.000001D);
        assertEquals(0.0495D, BloomGrowthPolicy.undoneSpawnChance(0.011D, 0.25F),
                0.000001D);
        assertEquals(0.144D, BloomGrowthPolicy.bloomboundSpawnChance(0.018D, 1.0F),
                0.000001D);
        assertEquals(1.0D, BloomGrowthPolicy.bloomboundSpawnChance(0.9D, 1.0F),
                0.0D);
        assertEquals(128.0D, BloomGrowthPolicy.undoneLocalCapRadius(0.0F), 0.0D);
        assertEquals(64.0D, BloomGrowthPolicy.undoneLocalCapRadius(1.0F), 0.0D);
    }
}
