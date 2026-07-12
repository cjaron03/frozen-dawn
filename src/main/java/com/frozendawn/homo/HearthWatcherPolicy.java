package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure gates and geometry for the first Hearth-bound watcher role.
 */
public final class HearthWatcherPolicy {
    public static final String PROFILE = "returned_watcher";
    public static final int MIN_SPAWN_RADIUS = 14;
    public static final int MAX_SPAWN_RADIUS = 20;
    public static final int SPAWN_ATTEMPTS = 24;
    public static final int HOME_RADIUS = 20;
    public static final int RETREAT_DISTANCE = 9;
    public static final int WATCH_DISTANCE = 28;

    private static final long SPAWN_SALT = 0x5741544348455231L;
    private static final long VARIANT_SALT = 0x5741544348564152L;

    private HearthWatcherPolicy() {
    }

    public static boolean canHostWatcher(ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.surfaceResolved()
                && hearth.structurePlaced()
                && hearth.structureStageApplied().ordinal()
                        >= ReturnedHearthSavedData.HearthStage.TRACE.ordinal();
    }

    public static List<BlockPos> spawnOffsets(long layoutSeed) {
        RandomSource random = RandomSource.create(mix(layoutSeed ^ SPAWN_SALT));
        double baseAngle = random.nextDouble() * Math.PI * 2.0D;
        List<BlockPos> offsets = new ArrayList<>(SPAWN_ATTEMPTS);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = baseAngle + attempt * (Math.PI * 2.0D / SPAWN_ATTEMPTS);
            int radius = MIN_SPAWN_RADIUS
                    + random.nextInt(MAX_SPAWN_RADIUS - MIN_SPAWN_RADIUS + 1);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            offsets.add(new BlockPos(x, 0, z));
        }
        return List.copyOf(offsets);
    }

    public static int textureVariant(long layoutSeed) {
        return Math.floorMod((int) mix(layoutSeed ^ VARIANT_SALT), 5);
    }

    public static boolean shouldRetreat(double playerDistanceSquared) {
        return playerDistanceSquared < (double) RETREAT_DISTANCE * RETREAT_DISTANCE;
    }

    public static boolean shouldReturnHome(double homeDistanceSquared) {
        return homeDistanceSquared > (double) HOME_RADIUS * HOME_RADIUS;
    }

    public static boolean canProactivelyTargetPlayer(
            boolean hearthBound, ReturnedHearthSavedData.HiveRelationship relationship) {
        return !hearthBound || relationship == ReturnedHearthSavedData.HiveRelationship.ORSATHAE;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
