package com.frozendawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Pure encounter tuning and region math for the Remnant. */
public final class RemnantPolicy {
    public static final int CHECK_INTERVAL_TICKS = 600;
    public static final double SPAWN_CHANCE_PER_CHECK = 0.004D;
    public static final int REGION_CHUNKS = 32;
    public static final int MIN_LURE_SPACING = 768;
    public static final int MAX_LOADED_LURES = 2;
    public static final long REPLACEMENT_COOLDOWN = 3L * 24_000L;
    public static final int OBSERVATION_TICKS = 100;
    public static final int SEALING_TICKS = 38;
    public static final int COLLAPSE_TICKS = 200;
    public static final int COLLAPSE_EDITS_PER_TICK = 6;
    public static final int DEATH_PRESENTATION_TICKS = 48;
    public static final int WALL_SLIP_TELEGRAPH = 16;
    public static final int WALL_SLIP_RECOVERY = 20;
    public static final int WALL_SLIP_COOLDOWN = 160;
    public static final double WALL_SLIP_MIN_DISTANCE_SQR = 6.25D;
    public static final int WALL_LATCH_TICKS = 50;
    public static final float WALL_LATCH_HEAL_PER_USE = 6.0F;
    public static final float WALL_LATCH_HEAL_BUDGET = 20.0F;
    public static final int LEARN_WINDOW_TICKS = 80;
    public static final int LEARNED_DODGE_COOLDOWN = 120;
    public static final int LEARNED_DODGE_FOLD_TICKS = 7;
    public static final int COUNTER_TOTAL_TICKS = 28;
    public static final int COUNTER_STRIKE_TICK = 7;
    public static final int COUNTER_COOLDOWN = 80;
    public static final float COUNTER_DAMAGE = 8.0F;
    public static final double COUNTER_REACH_SQR = 20.25D;
    public static final int REFLECTION_DELAY_TICKS = 30;
    public static final int REFLECTION_ATTACK_COOLDOWN = 12;
    public static final float REFLECTION_ATTACK_DAMAGE = 6.0F;
    public static final double REFLECTION_ATTACK_REACH_SQR = 10.24D;
    public static final int REFLECTION_GUARD_TICKS = 12;
    public static final float REFLECTION_GUARD_MULTIPLIER = 0.70F;
    public static final int RADIO_REPEAT_DELAY_MIN = 240;
    public static final int RADIO_REPEAT_DELAY_RANGE = 201;
    public static final int GRAB_MAX_TICKS = 80;
    public static final int GRAB_COOLDOWN = 300;
    public static final int LEASH_RADIUS = 24;

    private RemnantPolicy() {
    }

    public static long regionKey(BlockPos pos) {
        int regionX = Math.floorDiv(pos.getX() >> 4, REGION_CHUNKS);
        int regionZ = Math.floorDiv(pos.getZ() >> 4, REGION_CHUNKS);
        return (regionX & 0xffffffffL) | ((regionZ & 0xffffffffL) << 32);
    }

    public static boolean hasSpacing(BlockPos candidate, Collection<BlockPos> anchors) {
        long min = (long) MIN_LURE_SPACING * MIN_LURE_SPACING;
        return anchors.stream().allMatch(anchor -> anchor.distSqr(candidate) >= min);
    }

    public static boolean canNaturalPlace(boolean erased, boolean released,
                                          boolean regionOccupied, int loadedCount,
                                          long gameTime, long nextPlacementTime) {
        return erased && released && !regionOccupied
                && loadedCount < MAX_LOADED_LURES && gameTime >= nextPlacementTime;
    }

    public static boolean canEvadeRepeatedAttack(boolean bypassesLearning,
                                                  boolean samePattern,
                                                  int learnedHits,
                                                  int windowTicks,
                                                  int cooldownTicks) {
        return !bypassesLearning && samePattern && learnedHits >= 2
                && windowTicks > 0 && cooldownTicks <= 0;
    }

    public static boolean canStartWallSlip(double targetDistanceSqr, int cooldownTicks) {
        return cooldownTicks <= 0 && targetDistanceSqr >= WALL_SLIP_MIN_DISTANCE_SQR;
    }

    public static float wallLatchHealStep(float health, float maxHealth,
                                          float healedThisUse, float healedTotal) {
        float perTick = WALL_LATCH_HEAL_PER_USE / WALL_LATCH_TICKS;
        return Math.max(0.0F, Math.min(perTick, Math.min(
                maxHealth - health,
                Math.min(WALL_LATCH_HEAL_PER_USE - healedThisUse,
                        WALL_LATCH_HEAL_BUDGET - healedTotal))));
    }

    public static boolean canStartWallRecovery(float health, float maxHealth,
                                               float healedTotal) {
        return health < maxHealth - 0.01F && healedTotal < WALL_LATCH_HEAL_BUDGET;
    }

    /** Moves wall markers inward so folding never resolves inside the authored shell. */
    public static List<BlockPos> inwardSlipCandidates(BlockPos anchor, BlockPos origin) {
        int dx = Integer.compare(origin.getX(), anchor.getX());
        int dz = Integer.compare(origin.getZ(), anchor.getZ());
        Direction inward = Math.abs(origin.getX() - anchor.getX())
                >= Math.abs(origin.getZ() - anchor.getZ())
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction left = inward.getCounterClockWise();
        Direction right = inward.getClockWise();
        List<BlockPos> candidates = new ArrayList<>(9);
        for (int step = 1; step <= 3; step++) {
            BlockPos center = anchor.relative(inward, step);
            candidates.add(center);
            candidates.add(center.relative(left));
            candidates.add(center.relative(right));
        }
        return List.copyOf(candidates);
    }

    /** Cycles the common pleas while preserving a rare forgiveness transmission. */
    public static int radioLine(long layoutSeed, int broadcastCount) {
        long mixed = mix(layoutSeed + 0x9E3779B97F4A7C15L * (broadcastCount + 1L));
        int roll = Math.floorMod((int) (mixed ^ mixed >>> 32), 16);
        if (roll == 0) return 3;
        return Math.floorMod((int) (layoutSeed + broadcastCount), 3);
    }

    public static int radioRepeatDelay(long layoutSeed, int broadcastCount) {
        long mixed = mix(layoutSeed ^ 0xD1B54A32D192ED03L * (broadcastCount + 1L));
        return RADIO_REPEAT_DELAY_MIN
                + Math.floorMod((int) (mixed ^ mixed >>> 32), RADIO_REPEAT_DELAY_RANGE);
    }

    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
