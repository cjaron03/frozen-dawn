package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MonitoringStationState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_monitoring_stations";

    private final Set<Long> evaluatedStations = new HashSet<>();
    private final Set<Long> builtStations = new HashSet<>();
    private final Set<Long> unlockedStations = new HashSet<>();
    private final Map<Long, BlockPos> builtStationCenters = new HashMap<>();

    public static MonitoringStationState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MonitoringStationState::new, MonitoringStationState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static MonitoringStationState load(CompoundTag tag, HolderLookup.Provider registries) {
        MonitoringStationState state = new MonitoringStationState();
        if (tag.contains("evaluatedStations")) {
            for (long packed : tag.getLongArray("evaluatedStations")) {
                state.evaluatedStations.add(packed);
            }
        }
        if (tag.contains("builtStations")) {
            for (long packed : tag.getLongArray("builtStations")) {
                state.builtStations.add(packed);
                state.evaluatedStations.add(packed);
            }
        }
        if (tag.contains("builtStationCenters", Tag.TAG_LIST)) {
            ListTag centers = tag.getList("builtStationCenters", Tag.TAG_COMPOUND);
            for (int i = 0; i < centers.size(); i++) {
                CompoundTag entry = centers.getCompound(i);
                long packed = entry.getLong("chunk");
                BlockPos center = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                state.builtStationCenters.put(packed, center);
                state.builtStations.add(packed);
                state.evaluatedStations.add(packed);
            }
        }
        if (tag.contains("unlockedStations")) {
            for (long packed : tag.getLongArray("unlockedStations")) {
                state.unlockedStations.add(packed);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!evaluatedStations.isEmpty()) {
            tag.putLongArray("evaluatedStations", evaluatedStations.stream().mapToLong(Long::longValue).toArray());
        }
        if (!builtStations.isEmpty()) {
            tag.putLongArray("builtStations", builtStations.stream().mapToLong(Long::longValue).toArray());
        }
        if (!builtStationCenters.isEmpty()) {
            ListTag centers = new ListTag();
            for (Map.Entry<Long, BlockPos> entry : builtStationCenters.entrySet()) {
                CompoundTag compound = new CompoundTag();
                compound.putLong("chunk", entry.getKey());
                compound.putInt("x", entry.getValue().getX());
                compound.putInt("y", entry.getValue().getY());
                compound.putInt("z", entry.getValue().getZ());
                centers.add(compound);
            }
            tag.put("builtStationCenters", centers);
        }
        if (!unlockedStations.isEmpty()) {
            tag.putLongArray("unlockedStations", unlockedStations.stream().mapToLong(Long::longValue).toArray());
        }
        return tag;
    }

    public boolean isStationEvaluated(int chunkX, int chunkZ) {
        return evaluatedStations.contains(packChunkPos(chunkX, chunkZ));
    }

    public boolean isStationBuilt(int chunkX, int chunkZ) {
        return builtStations.contains(packChunkPos(chunkX, chunkZ));
    }

    public boolean isStationUnlocked(int chunkX, int chunkZ) {
        return unlockedStations.contains(packChunkPos(chunkX, chunkZ));
    }

    @Nullable
    public BlockPos getStationCenter(int chunkX, int chunkZ) {
        return builtStationCenters.get(packChunkPos(chunkX, chunkZ));
    }

    public Collection<BlockPos> getBuiltStationCenters() {
        return builtStationCenters.values();
    }

    public void markStationEvaluated(int chunkX, int chunkZ) {
        evaluatedStations.add(packChunkPos(chunkX, chunkZ));
        setDirty();
    }

    public void markStationBuilt(int chunkX, int chunkZ) {
        long key = packChunkPos(chunkX, chunkZ);
        evaluatedStations.add(key);
        builtStations.add(key);
        setDirty();
    }

    public void markStationBuilt(int chunkX, int chunkZ, BlockPos center) {
        long key = packChunkPos(chunkX, chunkZ);
        evaluatedStations.add(key);
        builtStations.add(key);
        builtStationCenters.put(key, center.immutable());
        setDirty();
    }

    public void markStationUnlocked(int chunkX, int chunkZ) {
        unlockedStations.add(packChunkPos(chunkX, chunkZ));
        setDirty();
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
