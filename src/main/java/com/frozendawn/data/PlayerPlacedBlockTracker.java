package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
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
 * Persistent world data tracking all player-placed block positions.
 * Used by ArchitectEntity's custom A* pathfinding to prioritize
 * breaking player-built structures over natural terrain.
 */
public class PlayerPlacedBlockTracker extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_player_blocks";

    private final Set<Long> placedBlocks = new HashSet<>();
    private long lastPruneTick = 0;
    private static final long PRUNE_INTERVAL = 6000; // 5 minutes

    public PlayerPlacedBlockTracker() {}

    public static PlayerPlacedBlockTracker get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerPlacedBlockTracker::new, PlayerPlacedBlockTracker::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static PlayerPlacedBlockTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerPlacedBlockTracker tracker = new PlayerPlacedBlockTracker();
        long[] arr = tag.getLongArray("placedBlocks");
        for (long l : arr) {
            tracker.placedBlocks.add(l);
        }
        tracker.lastPruneTick = tag.getLong("lastPruneTick");
        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("placedBlocks", placedBlocks.stream().mapToLong(Long::longValue).toArray());
        tag.putLong("lastPruneTick", lastPruneTick);
        return tag;
    }

    public void markPlaced(BlockPos pos) {
        if (placedBlocks.add(pos.asLong())) {
            setDirty();
        }
    }

    public void markRemoved(BlockPos pos) {
        if (placedBlocks.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        return placedBlocks.contains(pos.asLong());
    }

    public boolean isPlayerPlaced(long packed) {
        return placedBlocks.contains(packed);
    }

    /**
     * Remove stale entries where the block is now air.
     * Called periodically from WorldTickHandler.
     */
    public void prune(ServerLevel level) {
        long gameTick = level.getGameTime();
        if (gameTick - lastPruneTick < PRUNE_INTERVAL) return;
        lastPruneTick = gameTick;

        boolean changed = placedBlocks.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            // Only prune if chunk is loaded — don't force chunk loads
            if (!level.isLoaded(pos)) return false;
            return level.getBlockState(pos).isAir();
        });

        if (changed) {
            setDirty();
        }
    }

    public int size() {
        return placedBlocks.size();
    }
}
