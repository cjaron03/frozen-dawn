package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks loaded Acheron Forge positions per level for quick utility lookups.
 */
public final class AcheronForgeRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> forges = new WeakHashMap<>();

    private AcheronForgeRegistry() {}

    public static void register(Level level, BlockPos pos) {
        forges.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = forges.get(level);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                forges.remove(level);
            }
        }
    }

    public static Set<BlockPos> getForges(Level level) {
        Set<BlockPos> set = forges.get(level);
        return set != null ? set : Collections.emptySet();
    }
}
