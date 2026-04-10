package com.frozendawn.world;

import com.frozendawn.block.MiteAwayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public final class MiteAwayRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> burners = new WeakHashMap<>();

    private MiteAwayRegistry() {}

    public static void register(Level level, BlockPos pos) {
        burners.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = burners.get(level);
        if (set == null) {
            return;
        }
        set.remove(pos);
        if (set.isEmpty()) {
            burners.remove(level);
        }
    }

    public static Set<BlockPos> getBurners(Level level) {
        Set<BlockPos> set = burners.get(level);
        return set != null ? set : Collections.emptySet();
    }

    public static boolean isProtected(Level level, Vec3 pos) {
        return findNearestCoveringBurner(level, pos) != null;
    }

    public static boolean isProtected(Level level, BlockPos pos) {
        return isProtected(level, pos.getCenter());
    }

    public static @Nullable BlockPos findNearestCoveringBurner(Level level, Vec3 pos) {
        double maxDistanceSq = MiteAwayBlockEntity.COVERAGE_RADIUS * MiteAwayBlockEntity.COVERAGE_RADIUS;
        BlockPos nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (BlockPos burnerPos : getBurners(level)) {
            double distanceSq = burnerPos.getCenter().distanceToSqr(pos);
            if (distanceSq <= maxDistanceSq && distanceSq < nearestDistanceSq) {
                nearest = burnerPos.immutable();
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }
}
