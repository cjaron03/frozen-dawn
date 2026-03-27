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

public final class CargoDropState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_cargo_drops";

    private final Set<Long> evaluatedDrops = new HashSet<>();
    private final Set<Long> builtDrops = new HashSet<>();
    private final Map<Long, BlockPos> builtDropCenters = new HashMap<>();

    public static CargoDropState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CargoDropState::new, CargoDropState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static CargoDropState load(CompoundTag tag, HolderLookup.Provider registries) {
        CargoDropState state = new CargoDropState();
        if (tag.contains("evaluatedDrops")) {
            for (long packed : tag.getLongArray("evaluatedDrops")) {
                state.evaluatedDrops.add(packed);
            }
        }
        if (tag.contains("builtDrops")) {
            for (long packed : tag.getLongArray("builtDrops")) {
                state.builtDrops.add(packed);
                state.evaluatedDrops.add(packed);
            }
        }
        if (tag.contains("builtDropCenters", Tag.TAG_LIST)) {
            ListTag centers = tag.getList("builtDropCenters", Tag.TAG_COMPOUND);
            for (int i = 0; i < centers.size(); i++) {
                CompoundTag entry = centers.getCompound(i);
                long packed = entry.getLong("chunk");
                BlockPos center = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                state.builtDropCenters.put(packed, center);
                state.builtDrops.add(packed);
                state.evaluatedDrops.add(packed);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!evaluatedDrops.isEmpty()) {
            tag.putLongArray("evaluatedDrops", evaluatedDrops.stream().mapToLong(Long::longValue).toArray());
        }
        if (!builtDrops.isEmpty()) {
            tag.putLongArray("builtDrops", builtDrops.stream().mapToLong(Long::longValue).toArray());
        }
        if (!builtDropCenters.isEmpty()) {
            ListTag centers = new ListTag();
            for (Map.Entry<Long, BlockPos> entry : builtDropCenters.entrySet()) {
                CompoundTag compound = new CompoundTag();
                compound.putLong("chunk", entry.getKey());
                compound.putInt("x", entry.getValue().getX());
                compound.putInt("y", entry.getValue().getY());
                compound.putInt("z", entry.getValue().getZ());
                centers.add(compound);
            }
            tag.put("builtDropCenters", centers);
        }
        return tag;
    }

    public boolean isCargoDropEvaluated(int chunkX, int chunkZ) {
        return evaluatedDrops.contains(packChunkPos(chunkX, chunkZ));
    }

    public boolean isCargoDropBuilt(int chunkX, int chunkZ) {
        return builtDrops.contains(packChunkPos(chunkX, chunkZ));
    }

    @Nullable
    public BlockPos getCargoDropCenter(int chunkX, int chunkZ) {
        return builtDropCenters.get(packChunkPos(chunkX, chunkZ));
    }

    public Collection<BlockPos> getBuiltDropCenters() {
        return builtDropCenters.values();
    }

    public void markCargoDropEvaluated(int chunkX, int chunkZ) {
        evaluatedDrops.add(packChunkPos(chunkX, chunkZ));
        setDirty();
    }

    public void markCargoDropBuilt(int chunkX, int chunkZ, BlockPos center) {
        long key = packChunkPos(chunkX, chunkZ);
        evaluatedDrops.add(key);
        builtDrops.add(key);
        builtDropCenters.put(key, center.immutable());
        setDirty();
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
