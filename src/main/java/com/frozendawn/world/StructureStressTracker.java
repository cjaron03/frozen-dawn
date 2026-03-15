package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks temporary structural stress crack overlays for collapsing blocks.
 *
 * This is runtime-only state. It exists to show a staged "giving way" visual
 * before a block finally fails under cold and snow load.
 */
public final class StructureStressTracker {

    private static final long EXPIRE_TICKS = 400L;
    private static final long PRUNE_INTERVAL = 20L;
    private static final Map<ResourceKey<Level>, Map<Long, StressEntry>> ACTIVE = new HashMap<>();

    private StructureStressTracker() {
    }

    public static void update(ServerLevel level, BlockPos pos, int stage) {
        if (stage < 0) {
            clear(level, pos);
            return;
        }

        long packed = pos.asLong();
        long now = level.getGameTime();
        Map<Long, StressEntry> map = ACTIVE.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        StressEntry existing = map.get(packed);
        if (existing == null || existing.stage != stage) {
            level.destroyBlockProgress(overlayId(packed), pos, stage);
        }
        map.put(packed, new StressEntry(stage, now));
    }

    public static void clear(ServerLevel level, BlockPos pos) {
        Map<Long, StressEntry> map = ACTIVE.get(level.dimension());
        if (map == null) {
            return;
        }

        long packed = pos.asLong();
        if (map.remove(packed) != null) {
            level.destroyBlockProgress(overlayId(packed), pos, -1);
        }

        if (map.isEmpty()) {
            ACTIVE.remove(level.dimension());
        }
    }

    public static void prune(ServerLevel level) {
        if (level.getGameTime() % PRUNE_INTERVAL != 0) {
            return;
        }

        Map<Long, StressEntry> map = ACTIVE.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        Iterator<Map.Entry<Long, StressEntry>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, StressEntry> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            StressEntry stress = entry.getValue();

            boolean expired = now - stress.lastTouchedTick > EXPIRE_TICKS;
            boolean invalid = !level.isLoaded(pos) || level.getBlockState(pos).isAir();
            if (!expired && !invalid) {
                continue;
            }

            level.destroyBlockProgress(overlayId(entry.getKey()), pos, -1);
            iterator.remove();
        }

        if (map.isEmpty()) {
            ACTIVE.remove(level.dimension());
        }
    }

    public static void reset() {
        ACTIVE.clear();
    }

    private static int overlayId(long packed) {
        return 0x4F000000 ^ (int) (packed ^ (packed >>> 32));
    }

    private record StressEntry(int stage, long lastTouchedTick) {
    }
}
