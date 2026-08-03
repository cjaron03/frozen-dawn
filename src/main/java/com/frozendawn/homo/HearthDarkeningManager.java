package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.block.FrozenAtmosphereBlock;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, idempotent darkening for naturally loaded Hearth chunks. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HearthDarkeningManager {
    private static final int BLOCK_CHECKS_PER_TICK = 1_024;
    private static final int MIN_Y_OFFSET = -4;
    private static final int MAX_Y_OFFSET = 12;
    private static final int VERTICAL_SPAN = MAX_Y_OFFSET - MIN_Y_OFFSET + 1;
    private static final Map<Long, Integer> pendingChunks = new LinkedHashMap<>();
    private static boolean queuedLoadedChunks;

    private HearthDarkeningManager() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()
                || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ServerLevel.OVERWORLD
                || !PostMaeveWorldState.isErased(level)) {
            return;
        }
        queueIfHearthChunk(level, event.getChunk().getPos());
    }

    public static void tick(ServerLevel level) {
        if (!PostMaeveWorldState.isErased(level)) {
            reset();
            return;
        }
        if (!queuedLoadedChunks) {
            queueLoadedHearthChunks(level);
            queuedLoadedChunks = true;
        }
        if (pendingChunks.isEmpty()) {
            return;
        }

        Map.Entry<Long, Integer> work = pendingChunks.entrySet().iterator().next();
        long key = work.getKey();
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            pendingChunks.remove(key);
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        int cursor = work.getValue();
        int total = 16 * 16 * VERTICAL_SPAN;
        int end = Math.min(total, cursor + BLOCK_CHECKS_PER_TICK);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = cursor; index < end; index++) {
            int column = index / VERTICAL_SPAN;
            int yIndex = index % VERTICAL_SPAN;
            int localX = column & 15;
            int localZ = column >>> 4;
            int worldX = (chunkX << 4) + localX;
            int worldZ = (chunkZ << 4) + localZ;
            for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
                int radius = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                        ? 40 : 20;
                int dx = worldX - hearth.center().getX();
                int dz = worldZ - hearth.center().getZ();
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                pos.set(worldX,
                        hearth.center().getY() + MIN_Y_OFFSET + yIndex,
                        worldZ);
                darken(level, pos);
                break;
            }
        }

        if (end >= total) {
            pendingChunks.remove(key);
        } else {
            pendingChunks.put(key, end);
        }
    }

    public static void begin(ServerLevel level) {
        pendingChunks.clear();
        queuedLoadedChunks = false;
        queueLoadedHearthChunks(level);
        queuedLoadedChunks = true;
    }

    public static void reset() {
        pendingChunks.clear();
        queuedLoadedChunks = false;
    }

    public static int queuedChunks() {
        return pendingChunks.size();
    }

    private static void queueLoadedHearthChunks(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            int radius = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                    ? 40 : 20;
            int minChunkX = (hearth.center().getX() - radius) >> 4;
            int maxChunkX = (hearth.center().getX() + radius) >> 4;
            int minChunkZ = (hearth.center().getZ() - radius) >> 4;
            int maxChunkZ = (hearth.center().getZ() + radius) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                        pendingChunks.putIfAbsent(
                                ChunkPos.asLong(chunkX, chunkZ), 0);
                    }
                }
            }
        }
    }

    private static void queueIfHearthChunk(ServerLevel level, ChunkPos chunk) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        int minX = chunk.getMinBlockX();
        int maxX = chunk.getMaxBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxZ = chunk.getMaxBlockZ();
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            int radius = hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                    ? 40 : 20;
            int nearestX = Math.max(minX, Math.min(maxX, hearth.center().getX()));
            int nearestZ = Math.max(minZ, Math.min(maxZ, hearth.center().getZ()));
            int dx = nearestX - hearth.center().getX();
            int dz = nearestZ - hearth.center().getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                pendingChunks.putIfAbsent(chunk.toLong(), 0);
                return;
            }
        }
    }

    private static void darken(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                && !state.getValue(AcheroniteCrystalBlock.DARK)) {
            level.setBlock(pos, state.setValue(AcheroniteCrystalBlock.DARK, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } else if (state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                && !state.getValue(FrozenAtmosphereBlock.DARK)) {
            level.setBlock(pos, state.setValue(FrozenAtmosphereBlock.DARK, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
