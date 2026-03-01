package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player sanity data. Saved with the overworld.
 */
public class SanityState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_sanity";

    private final Map<UUID, Integer> isolationTicks = new HashMap<>();
    private final Map<UUID, Integer> comfortGraceTicks = new HashMap<>();

    public SanityState() {}

    public static SanityState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SanityState::new, SanityState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static SanityState load(CompoundTag tag, HolderLookup.Provider registries) {
        SanityState state = new SanityState();
        CompoundTag isolation = tag.getCompound("isolationTicks");
        for (String key : isolation.getAllKeys()) {
            try {
                state.isolationTicks.put(UUID.fromString(key), isolation.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag grace = tag.getCompound("comfortGraceTicks");
        for (String key : grace.getAllKeys()) {
            try {
                state.comfortGraceTicks.put(UUID.fromString(key), grace.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag isolation = new CompoundTag();
        for (var entry : isolationTicks.entrySet()) {
            isolation.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("isolationTicks", isolation);

        CompoundTag grace = new CompoundTag();
        for (var entry : comfortGraceTicks.entrySet()) {
            grace.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("comfortGraceTicks", grace);
        return tag;
    }

    public int getIsolationTicks(UUID id) {
        return isolationTicks.getOrDefault(id, 0);
    }

    public void setIsolationTicks(UUID id, int ticks) {
        if (ticks > 0) {
            isolationTicks.put(id, ticks);
        } else {
            isolationTicks.remove(id);
        }
        setDirty();
    }

    public int getComfortGrace(UUID id) {
        return comfortGraceTicks.getOrDefault(id, 0);
    }

    public void setComfortGrace(UUID id, int ticks) {
        if (ticks > 0) {
            comfortGraceTicks.put(id, ticks);
        } else {
            comfortGraceTicks.remove(id);
        }
        setDirty();
    }

    public void clearPlayer(UUID id) {
        isolationTicks.remove(id);
        comfortGraceTicks.remove(id);
        setDirty();
    }

    public void clearAll() {
        isolationTicks.clear();
        comfortGraceTicks.clear();
        setDirty();
    }
}
