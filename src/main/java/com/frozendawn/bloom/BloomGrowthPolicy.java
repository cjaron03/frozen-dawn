package com.frozendawn.bloom;

import com.frozendawn.config.ConfigPresets;
import net.minecraft.util.Mth;

/** Pure deterministic policy for Bloom timing, density, and overlap. */
public final class BloomGrowthPolicy {
    public static final int INITIAL_RADIUS = 12;
    public static final int PRIMARY_RADIUS = 800;
    public static final int MAX_RADIUS = 1_000;
    public static final long DAY_TICKS = 24_000L;

    private BloomGrowthPolicy() {
    }

    public static long presetDurationTicks(String presetName) {
        ConfigPresets preset;
        try {
            preset = ConfigPresets.valueOf(presetName == null ? "DEFAULT" : presetName);
        } catch (IllegalArgumentException ignored) {
            preset = ConfigPresets.DEFAULT;
        }
        return switch (preset) {
            case BRUTAL -> 15L * DAY_TICKS;
            case CINEMATIC -> 60L * DAY_TICKS;
            case DEFAULT -> 30L * DAY_TICKS;
        };
    }

    public static double radius(long activeTicks, long presetDurationTicks) {
        if (activeTicks <= 0L) {
            return INITIAL_RADIUS;
        }
        long safeDuration = Math.max(1L, presetDurationTicks);
        double blocksPerTick = (PRIMARY_RADIUS - INITIAL_RADIUS) / (double) safeDuration;
        return Math.min(MAX_RADIUS, INITIAL_RADIUS + activeTicks * blocksPerTick);
    }

    public static BloomBand band(double distance, double radius) {
        if (distance <= Math.min(150.0D, radius * 0.20D)) {
            return BloomBand.CORE;
        }
        if (distance <= radius * 0.58D) {
            return BloomBand.MID;
        }
        return BloomBand.FRONTIER;
    }

    public static double coverage(BloomBand band, long deterministicBits, int overlapCount) {
        double unit = ((deterministicBits >>> 11) & 0xFFFFL) / 65535.0D;
        double base = switch (band) {
            case FRONTIER -> Mth.lerp(unit, 0.03D, 0.06D);
            case MID -> Mth.lerp(unit, 0.18D, 0.28D);
            case CORE -> Mth.lerp(unit, 0.55D, 0.70D);
        };
        return Math.min(0.85D, base + Math.max(0, overlapCount - 1) * 0.25D);
    }

    public static int maxHeight(BloomBand band, long deterministicBits, int overlapCount) {
        int base = switch (band) {
            case FRONTIER -> 2;
            case MID -> 4 + (int) Math.floorMod(deterministicBits, 9L);
            case CORE -> 18 + (int) Math.floorMod(deterministicBits, 23L);
        };
        return Math.min(30, base + Math.max(0, overlapCount - 1) * 8);
    }

    public static long sealedLifetimeTicks(BloomBand band) {
        return switch (band) {
            case FRONTIER -> 8L * DAY_TICKS;
            case MID -> 3L * DAY_TICKS;
            case CORE -> (3L * DAY_TICKS) / 2L;
        };
    }

    public static double undoneSpawnChance(double baseChance, float localDensity) {
        return scaledSpawnChance(baseChance, localDensity, 8.0D);
    }

    public static double bloomboundSpawnChance(double baseChance, float localDensity) {
        return scaledSpawnChance(baseChance, localDensity, 8.0D);
    }

    public static double undoneLocalCapRadius(float localDensity) {
        return Mth.lerp(Mth.clamp(localDensity, 0.0F, 1.0F), 128.0D, 64.0D);
    }

    private static double scaledSpawnChance(double baseChance, float localDensity,
                                            double maximumMultiplier) {
        double density = Mth.clamp(localDensity, 0.0F, 1.0F);
        // Even sparse visible Bloom should announce the post-Maeve ecology.
        double multiplier = Mth.lerp(Math.sqrt(density), 1.0D, maximumMultiplier);
        return Mth.clamp(baseChance * multiplier, 0.0D, 1.0D);
    }

    public static int sealedWearStage(long contactTicks, BloomBand band) {
        long lifetime = sealedLifetimeTicks(band);
        if (contactTicks >= lifetime) {
            return 4;
        }
        return Math.min(3, (int) ((contactTicks * 4L) / lifetime));
    }

    public static long chunkSeed(long worldSeed, long hearthLayoutSeed, long chunkPos) {
        return mix(worldSeed ^ hearthLayoutSeed ^ chunkPos ^ 0x424C4F4F4D534545L);
    }

    public static int initialTipAttempts(long chunkSeed) {
        return 1 + (int) Math.floorMod(chunkSeed, 3L);
    }

    public static int spentLatticeDrops(BloomBand band, float roll) {
        return switch (band) {
            case FRONTIER -> 0;
            case MID -> roll < 0.25F ? 1 : 0;
            case CORE -> 1 + (roll < 0.25F ? 1 : 0);
        };
    }

    public static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
