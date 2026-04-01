package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class FrozenTownState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_frozen_towns";

    private final Set<Long> processedChunks = new HashSet<>();
    private final Set<Long> configuredFlags = new HashSet<>();

    public static FrozenTownState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FrozenTownState::new, FrozenTownState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static FrozenTownState load(CompoundTag tag, HolderLookup.Provider registries) {
        FrozenTownState state = new FrozenTownState();
        if (tag.contains("processedChunks")) {
            for (long packed : tag.getLongArray("processedChunks")) {
                state.processedChunks.add(packed);
            }
        }
        if (tag.contains("configuredFlags")) {
            for (long packed : tag.getLongArray("configuredFlags")) {
                state.configuredFlags.add(packed);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!processedChunks.isEmpty()) {
            tag.putLongArray("processedChunks", processedChunks.stream().mapToLong(Long::longValue).toArray());
        }
        if (!configuredFlags.isEmpty()) {
            tag.putLongArray("configuredFlags", configuredFlags.stream().mapToLong(Long::longValue).toArray());
        }
        return tag;
    }

    public boolean isChunkProcessed(int chunkX, int chunkZ) {
        return processedChunks.contains(packChunkPos(chunkX, chunkZ));
    }

    public void markChunkProcessed(int chunkX, int chunkZ) {
        if (processedChunks.add(packChunkPos(chunkX, chunkZ))) {
            setDirty();
        }
    }

    public boolean isFlagConfigured(long flagPos) {
        return configuredFlags.contains(flagPos);
    }

    public void markFlagConfigured(long flagPos) {
        if (configuredFlags.add(flagPos)) {
            setDirty();
        }
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
