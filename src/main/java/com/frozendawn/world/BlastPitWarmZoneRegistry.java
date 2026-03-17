package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks registered Blast Pit warm-zone anchors for passive warmth coverage.
 * Future Blast Pit blocks should register on placement and unregister on removal.
 */
public final class BlastPitWarmZoneRegistry {

    private static final int WARM_ZONE_RADIUS = 18;
    private static final int MAX_VERTICAL_DRIFT = 18;
    private static final WeakHashMap<Level, Set<BlockPos>> warmZones = new WeakHashMap<>();

    private BlastPitWarmZoneRegistry() {}

    public static void register(Level level, BlockPos center) {
        warmZones.computeIfAbsent(level, k -> new HashSet<>()).add(center.immutable());
    }

    public static void unregister(Level level, BlockPos center) {
        Set<BlockPos> set = warmZones.get(level);
        if (set != null) {
            set.remove(center);
            if (set.isEmpty()) warmZones.remove(level);
        }
    }

    public static boolean isInsideWarmZone(Level level, BlockPos pos) {
        Set<BlockPos> set = warmZones.get(level);
        if (set == null) return false;
        double radiusSq = WARM_ZONE_RADIUS * WARM_ZONE_RADIUS;
        for (BlockPos center : set) {
            if (Math.abs(pos.getY() - center.getY()) > MAX_VERTICAL_DRIFT) {
                continue;
            }
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if ((dx * dx) + (dz * dz) <= radiusSq) return true;
        }
        return false;
    }

    public static int getRadius() {
        return WARM_ZONE_RADIUS;
    }

    public static void reset() {
        warmZones.clear();
    }
}
