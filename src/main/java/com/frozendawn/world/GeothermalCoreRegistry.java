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

    private static final WeakHashMap<Level, java.util.Map<Long, Set<BlockPos>>> chunks = new WeakHashMap<>();

    private GeothermalCoreRegistry() {}

    public static void register(Level level, BlockPos pos) {
        cores.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
        chunks.computeIfAbsent(level, k -> new java.util.HashMap<>()).computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new java.util.LinkedHashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        var index = chunks.get(level);
        if (index != null) {
            long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            var positions = index.get(key); if (positions != null) { positions.remove(pos); if (positions.isEmpty()) index.remove(key); }
            if (index.isEmpty()) chunks.remove(level);
        }
        Set<BlockPos> set = cores.get(level);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) cores.remove(level);
        }
    }

    public static java.util.List<BlockPos> nearby(Level level, BlockPos origin, int radius, int limit) {
        var index = chunks.get(level); if (index == null) return java.util.List.of();
        var result = new java.util.ArrayList<BlockPos>(); int checked = 0;
        radius = Math.min(24, Math.max(0, radius)); limit = Math.min(16, Math.max(0, limit));
        if (limit == 0) return java.util.List.of();
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++)
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                var positions = index.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
                if (positions == null || !level.hasChunk(x, z)) continue;
                for (var pos : positions) {
                    if (checked++ >= 64) return java.util.List.copyOf(result);
                    if (pos.distSqr(origin) <= radius * radius) result.add(pos);
                    if (result.size() == limit) return java.util.List.copyOf(result);
                }
            }
        return java.util.List.copyOf(result);
    }

    public static Set<BlockPos> getCores(Level level) {
        Set<BlockPos> set = cores.get(level);
        return set != null ? set : Collections.emptySet();
    }
}
