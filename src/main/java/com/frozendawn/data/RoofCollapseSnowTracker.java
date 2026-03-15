package com.frozendawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks snow that was dumped indoors by a roof collapse so the normal
 * precipitation cleanup pass does not delete it as stray indoor snow.
 */
public final class RoofCollapseSnowTracker extends SavedData {

    private static final String DATA_NAME = "frozendawn_roof_collapse_snow";
    private static final long PRUNE_INTERVAL = 200L;

    private final Set<Long> collapsedSnow = new HashSet<>();
    private long lastPruneTick = 0L;

    public static RoofCollapseSnowTracker get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RoofCollapseSnowTracker::new, RoofCollapseSnowTracker::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static RoofCollapseSnowTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        RoofCollapseSnowTracker tracker = new RoofCollapseSnowTracker();
        for (long packed : tag.getLongArray("collapsedSnow")) {
            tracker.collapsedSnow.add(packed);
        }
        tracker.lastPruneTick = tag.getLong("lastPruneTick");
        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("collapsedSnow", collapsedSnow.stream().mapToLong(Long::longValue).toArray());
        tag.putLong("lastPruneTick", lastPruneTick);
        return tag;
    }

    public void mark(BlockPos pos) {
        if (collapsedSnow.add(pos.asLong())) {
            setDirty();
        }
    }

    public void clear(BlockPos pos) {
        if (collapsedSnow.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean contains(BlockPos pos) {
        return collapsedSnow.contains(pos.asLong());
    }

    public void prune(ServerLevel level) {
        long gameTick = level.getGameTime();
        if (gameTick - lastPruneTick < PRUNE_INTERVAL) {
            return;
        }
        lastPruneTick = gameTick;

        boolean changed = collapsedSnow.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                return false;
            }

            return !(level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SNOW)
                    || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK));
        });

        if (changed) {
            setDirty();
        }
    }
}
