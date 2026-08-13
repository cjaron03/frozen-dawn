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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Persistent world data tracking all player-placed block positions.
 * Used by ArchitectEntity's custom A* pathfinding to prioritize
 * breaking player-built structures over natural terrain.
 */
public class PlayerPlacedBlockTracker extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_player_blocks";

    private final Set<Long> placedBlocks = new HashSet<>();
    private final Map<Long, Set<Long>> blocksByChunk = new HashMap<>();
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
            tracker.index(l);
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
        long packed = pos.asLong();
        if (placedBlocks.add(packed)) {
            index(packed);
            setDirty();
        }
    }

    public void markRemoved(BlockPos pos) {
        long packed = pos.asLong();
        if (placedBlocks.remove(packed)) {
            unindex(packed);
            setDirty();
        }
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        return placedBlocks.contains(pos.asLong());
    }

    public boolean isPlayerPlaced(long packed) {
        return placedBlocks.contains(packed);
    }

    /** Bounded spatial query used by rare structure placement preflights. */
    public boolean hasPlayerPlacedWithin(BlockPos center, int horizontalRadius,
                                         int verticalRadius) {
        int minChunkX = (center.getX() - horizontalRadius) >> 4;
        int maxChunkX = (center.getX() + horizontalRadius) >> 4;
        int minChunkZ = (center.getZ() - horizontalRadius) >> 4;
        int maxChunkZ = (center.getZ() + horizontalRadius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<Long> positions = blocksByChunk.get(packChunk(chunkX, chunkZ));
                if (positions == null) continue;
                for (long packed : positions) {
                    BlockPos pos = BlockPos.of(packed);
                    if (Math.abs(pos.getX() - center.getX()) <= horizontalRadius
                            && Math.abs(pos.getZ() - center.getZ()) <= horizontalRadius
                            && Math.abs(pos.getY() - center.getY()) <= verticalRadius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Remove stale entries where the block is now air.
     * Called periodically from WorldTickHandler.
     */
    public void prune(ServerLevel level) {
        long gameTick = level.getGameTime();
        if (gameTick - lastPruneTick < PRUNE_INTERVAL) return;
        lastPruneTick = gameTick;

        Set<Long> removed = new HashSet<>();
        boolean changed = placedBlocks.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            // Only prune if chunk is loaded — don't force chunk loads
            if (!level.isLoaded(pos)) return false;
            boolean stale = level.getBlockState(pos).isAir();
            if (stale) removed.add(packed);
            return stale;
        });

        if (changed) {
            removed.forEach(this::unindex);
            setDirty();
        }
    }

    public int size() {
        return placedBlocks.size();
    }

    private void index(long packed) {
        BlockPos pos = BlockPos.of(packed);
        blocksByChunk.computeIfAbsent(packChunk(pos.getX() >> 4, pos.getZ() >> 4),
                ignored -> new HashSet<>()).add(packed);
    }

    private void unindex(long packed) {
        BlockPos pos = BlockPos.of(packed);
        long chunk = packChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Set<Long> positions = blocksByChunk.get(chunk);
        if (positions == null) return;
        positions.remove(packed);
        if (positions.isEmpty()) blocksByChunk.remove(chunk);
    }

    private static long packChunk(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }
}
