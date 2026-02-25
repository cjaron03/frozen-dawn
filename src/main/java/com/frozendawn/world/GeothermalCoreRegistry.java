package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks loaded Geothermal Core positions per level for efficient distance lookups.
 * Avoids O(r^3) block scanning for large radii (up to 32 blocks).
 */
public final class GeothermalCoreRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> cores = new WeakHashMap<>();

    private GeothermalCoreRegistry() {}

    public static void register(Level level, BlockPos pos) {
        cores.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = cores.get(level);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) cores.remove(level);
        }
    }

    public static Set<BlockPos> getCores(Level level) {
        Set<BlockPos> set = cores.get(level);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }
}
