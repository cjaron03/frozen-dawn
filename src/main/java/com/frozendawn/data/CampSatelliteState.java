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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CampSatelliteState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_camp_satellites";

    private final Map<Long, VehicleRecord> campVehicles = new HashMap<>();
    private transient Map<Long, Long> vehicleChunkToCamp;

    public static CampSatelliteState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CampSatelliteState::new, CampSatelliteState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static CampSatelliteState load(CompoundTag tag, HolderLookup.Provider registries) {
        CampSatelliteState state = new CampSatelliteState();
        if (tag.contains("campVehicles", Tag.TAG_LIST)) {
            ListTag list = tag.getList("campVehicles", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                long campKey = entry.getLong("camp");
                VehicleRecord record = VehicleRecord.load(entry);
                if (record != null) {
                    state.campVehicles.put(campKey, record);
                }
            }
        }
        state.rebuildIndexes();
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!campVehicles.isEmpty()) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, VehicleRecord> entry : campVehicles.entrySet()) {
                CompoundTag compound = entry.getValue().save();
                compound.putLong("camp", entry.getKey());
                list.add(compound);
            }
            tag.put("campVehicles", list);
        }
        return tag;
    }

    public boolean hasDecision(int campChunkX, int campChunkZ) {
        return campVehicles.containsKey(packChunkPos(campChunkX, campChunkZ));
    }

    public boolean hasLinkedVehicle(int campChunkX, int campChunkZ) {
        VehicleRecord record = campVehicles.get(packChunkPos(campChunkX, campChunkZ));
        return record != null && record.hasVehicle();
    }

    public boolean isVehicleBuilt(int campChunkX, int campChunkZ) {
        VehicleRecord record = campVehicles.get(packChunkPos(campChunkX, campChunkZ));
        return record != null && record.vehicleBuilt();
    }

    @Nullable
    public BlockPos getCampCenter(int campChunkX, int campChunkZ) {
        VehicleRecord record = campVehicles.get(packChunkPos(campChunkX, campChunkZ));
        return record != null ? record.campCenter() : null;
    }

    @Nullable
    public BlockPos getVehicleCenter(int campChunkX, int campChunkZ) {
        VehicleRecord record = campVehicles.get(packChunkPos(campChunkX, campChunkZ));
        return record != null ? record.vehicleCenter() : null;
    }

    public int getVehicleVariant(int campChunkX, int campChunkZ) {
        VehicleRecord record = campVehicles.get(packChunkPos(campChunkX, campChunkZ));
        return record != null ? record.variantId() : -1;
    }

    @Nullable
    public Long getCampKeyForVehicleChunk(int vehicleChunkX, int vehicleChunkZ) {
        rebuildIndexesIfNeeded();
        return vehicleChunkToCamp.get(packChunkPos(vehicleChunkX, vehicleChunkZ));
    }

    public List<Long> getPendingVehicleCampKeys() {
        List<Long> pendingKeys = new ArrayList<>();
        for (Map.Entry<Long, VehicleRecord> entry : campVehicles.entrySet()) {
            VehicleRecord record = entry.getValue();
            if (record.hasVehicle() && !record.vehicleBuilt()) {
                pendingKeys.add(entry.getKey());
            }
        }
        return pendingKeys;
    }

    public void markNoVehicle(int campChunkX, int campChunkZ, BlockPos campCenter) {
        campVehicles.put(packChunkPos(campChunkX, campChunkZ),
                new VehicleRecord(campCenter.immutable(), null, -1, false));
        rebuildIndexes();
        setDirty();
    }

    public void markVehiclePlanned(int campChunkX, int campChunkZ, BlockPos campCenter,
                                   BlockPos vehicleCenter, int variantId) {
        campVehicles.put(packChunkPos(campChunkX, campChunkZ),
                new VehicleRecord(campCenter.immutable(), vehicleCenter.immutable(), variantId, false));
        rebuildIndexes();
        setDirty();
    }

    public void markVehicleBuilt(int campChunkX, int campChunkZ) {
        long key = packChunkPos(campChunkX, campChunkZ);
        VehicleRecord existing = campVehicles.get(key);
        if (existing == null || !existing.hasVehicle() || existing.vehicleBuilt()) {
            return;
        }
        campVehicles.put(key, new VehicleRecord(existing.campCenter(), existing.vehicleCenter(), existing.variantId(), true));
        rebuildIndexes();
        setDirty();
    }

    private void rebuildIndexesIfNeeded() {
        if (vehicleChunkToCamp == null) {
            rebuildIndexes();
        }
    }

    private void rebuildIndexes() {
        vehicleChunkToCamp = new HashMap<>();
        for (Map.Entry<Long, VehicleRecord> entry : campVehicles.entrySet()) {
            VehicleRecord record = entry.getValue();
            if (!record.hasVehicle() || record.vehicleCenter() == null) {
                continue;
            }
            vehicleChunkToCamp.put(packChunkPos(record.vehicleCenter().getX() >> 4, record.vehicleCenter().getZ() >> 4), entry.getKey());
        }
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private record VehicleRecord(BlockPos campCenter, @Nullable BlockPos vehicleCenter, int variantId, boolean vehicleBuilt) {

        boolean hasVehicle() {
            return vehicleCenter != null && variantId >= 0;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("campX", campCenter.getX());
            tag.putInt("campY", campCenter.getY());
            tag.putInt("campZ", campCenter.getZ());
            tag.putBoolean("hasVehicle", hasVehicle());
            tag.putBoolean("vehicleBuilt", vehicleBuilt);
            tag.putInt("variantId", variantId);
            if (vehicleCenter != null) {
                tag.putInt("vehicleX", vehicleCenter.getX());
                tag.putInt("vehicleY", vehicleCenter.getY());
                tag.putInt("vehicleZ", vehicleCenter.getZ());
            }
            return tag;
        }

        @Nullable
        static VehicleRecord load(CompoundTag tag) {
            if (!tag.contains("campX") || !tag.contains("campY") || !tag.contains("campZ")) {
                return null;
            }
            BlockPos campCenter = new BlockPos(tag.getInt("campX"), tag.getInt("campY"), tag.getInt("campZ"));
            BlockPos vehicleCenter = null;
            if (tag.getBoolean("hasVehicle") && tag.contains("vehicleX") && tag.contains("vehicleY") && tag.contains("vehicleZ")) {
                vehicleCenter = new BlockPos(tag.getInt("vehicleX"), tag.getInt("vehicleY"), tag.getInt("vehicleZ"));
            }
            return new VehicleRecord(campCenter, vehicleCenter, tag.getInt("variantId"), tag.getBoolean("vehicleBuilt"));
        }
    }
}
