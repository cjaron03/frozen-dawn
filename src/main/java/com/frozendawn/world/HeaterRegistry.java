package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks lit Thermal Heater positions per level for efficient distance lookups.
 * Registered when a heater lights up, unregistered when it burns out or is removed.
 * Eliminates O(r^3) block scanning in TemperatureManager.
 */
public final class HeaterRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> heaters = new WeakHashMap<>();

    private HeaterRegistry() {}

    public static void register(Level level, BlockPos pos) {
        heaters.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = heaters.get(level);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) heaters.remove(level);
        }
    }

    public static Set<BlockPos> getHeaters(Level level) {
        Set<BlockPos> set = heaters.get(level);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }
}
