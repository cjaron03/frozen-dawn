package com.frozendawn.world;

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

import java.util.HashMap;
import java.util.Map;

public class ThermalVentSavedData extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_thermal_vents";

    private final Map<Long, VentRecord> records = new HashMap<>();

    public static ThermalVentSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ThermalVentSavedData::new, ThermalVentSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static ThermalVentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ThermalVentSavedData state = new ThermalVentSavedData();
        ListTag entries = tag.getList("Vents", Tag.TAG_COMPOUND);
        for (Tag entry : entries) {
            if (!(entry instanceof CompoundTag ventTag)) {
                continue;
            }
            VentRecord record = VentRecord.fromTag(ventTag);
            state.records.put(packRegion(record.regionX(), record.regionZ()), record);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (VentRecord record : records.values()) {
            entries.add(record.toTag());
        }
        tag.put("Vents", entries);
        return tag;
    }

    @Nullable
    public VentRecord getOrCreate(ServerLevel level, int regionX, int regionZ) {
        long key = packRegion(regionX, regionZ);
        VentRecord existing = records.get(key);
        if (existing != null) {
            return existing;
        }

        VentRecord created = ThermalVentSystem.createVentRecord(level, regionX, regionZ);
        if (created == null) {
            return null;
        }

        records.put(key, created);
        setDirty();
        return created;
    }

    public void markDirty() {
        setDirty();
    }

    private static long packRegion(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
    }

    public static final class VentRecord {
        private final int regionX;
        private final int regionZ;
        private final int x;
        private final int z;
        private final ThermalVentArchetype archetype;
        private int y = Integer.MIN_VALUE;
        private boolean surfaced;
        private boolean spent;
        private long activatedAt = -1L;
        private long nextEventTick = -1L;
        private long eruptionEndTick = -1L;

        public VentRecord(int regionX, int regionZ, int x, int z, ThermalVentArchetype archetype) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.x = x;
            this.z = z;
            this.archetype = archetype;
        }

        public int regionX() {
            return regionX;
        }

        public int regionZ() {
            return regionZ;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }

        public ThermalVentArchetype archetype() {
            return archetype;
        }

        public int y() {
            return y;
        }

        public boolean hasResolvedSurface() {
            return y != Integer.MIN_VALUE;
        }

        public void setY(int y) {
            this.y = y;
        }

        public boolean surfaced() {
            return surfaced;
        }

        public void setSurfaced(boolean surfaced) {
            this.surfaced = surfaced;
        }

        public boolean spent() {
            return spent;
        }

        public void setSpent(boolean spent) {
            this.spent = spent;
        }

        public long activatedAt() {
            return activatedAt;
        }

        public void setActivatedAt(long activatedAt) {
            this.activatedAt = activatedAt;
        }

        public long nextEventTick() {
            return nextEventTick;
        }

        public void setNextEventTick(long nextEventTick) {
            this.nextEventTick = nextEventTick;
        }

        public long eruptionEndTick() {
            return eruptionEndTick;
        }

        public void setEruptionEndTick(long eruptionEndTick) {
            this.eruptionEndTick = eruptionEndTick;
        }

        public BlockPos anchorPos() {
            return new BlockPos(x, hasResolvedSurface() ? y : 0, z);
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("RegionX", regionX);
            tag.putInt("RegionZ", regionZ);
            tag.putInt("X", x);
            tag.putInt("Z", z);
            tag.putString("Archetype", archetype.getSerializedName());
            tag.putInt("Y", y);
            tag.putBoolean("Surfaced", surfaced);
            tag.putBoolean("Spent", spent);
            tag.putLong("ActivatedAt", activatedAt);
            tag.putLong("NextEventTick", nextEventTick);
            tag.putLong("EruptionEndTick", eruptionEndTick);
            return tag;
        }

        private static VentRecord fromTag(CompoundTag tag) {
            String archetypeName = tag.getString("Archetype");
            ThermalVentArchetype archetype = switch (archetypeName) {
                case "active" -> ThermalVentArchetype.ACTIVE;
                case "rupture" -> ThermalVentArchetype.RUPTURE;
                default -> ThermalVentArchetype.WARM;
            };
            VentRecord record = new VentRecord(
                    tag.getInt("RegionX"),
                    tag.getInt("RegionZ"),
                    tag.getInt("X"),
                    tag.getInt("Z"),
                    archetype
            );
            record.y = tag.getInt("Y");
            record.surfaced = tag.getBoolean("Surfaced");
            record.spent = tag.getBoolean("Spent");
            record.activatedAt = tag.getLong("ActivatedAt");
            record.nextEventTick = tag.getLong("NextEventTick");
            record.eruptionEndTick = tag.getLong("EruptionEndTick");
            return record;
        }
    }
}
