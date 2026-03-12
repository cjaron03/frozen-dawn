package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks loaded ORSA Transponder positions per level for quick utility lookups.
 */
public final class TransponderRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> transponders = new WeakHashMap<>();

    private TransponderRegistry() {}

    public static void register(Level level, BlockPos pos) {
        transponders.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = transponders.get(level);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                transponders.remove(level);
            }
        }
    }

    public static Set<BlockPos> getTransponders(Level level) {
        Set<BlockPos> set = transponders.get(level);
        return set != null ? set : Collections.emptySet();
    }
}
