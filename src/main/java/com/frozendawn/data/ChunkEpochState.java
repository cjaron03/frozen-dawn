package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-chunk apocalypse catch-up state.
 *
 * This deliberately stores coarse transform epochs, not every missed random tick.
 */
public final class ChunkEpochState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_chunk_epochs";

    private final Map<Long, Record> records = new HashMap<>();

    public static ChunkEpochState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkEpochState::new, ChunkEpochState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static ChunkEpochState load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkEpochState state = new ChunkEpochState();
        ListTag list = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (entry instanceof CompoundTag compound) {
                Record record = Record.load(compound);
                state.records.put(record.chunkKey, record);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record record : records.values()) {
            list.add(record.save());
        }
        tag.put("chunks", list);
        return tag;
    }

    public Record getOrCreate(int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        return records.computeIfAbsent(key, ignored -> new Record(key, chunkX, chunkZ));
    }

    public Record get(int chunkX, int chunkZ) {
        return records.get(pack(chunkX, chunkZ));
    }

    public boolean needsCatchUp(int chunkX, int chunkZ, int transformVersion, int targetDay) {
        Record record = get(chunkX, chunkZ);
        return record == null
                || !record.complete
                || record.transformVersion < transformVersion
                || record.targetDay < targetDay;
    }

    public int recordCount() {
        return records.size();
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackChunkX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackChunkZ(long key) {
        return (int) key;
    }

    public static final class Record {
        private final long chunkKey;
        private final int chunkX;
        private final int chunkZ;
        private int transformVersion;
        private int targetDay;
        private float targetProgress;
        private int targetPhase;
        private int passIndex;
        private int cursor;
        private boolean complete;
        private long updatedGameTime;

        private Record(long chunkKey, int chunkX, int chunkZ) {
            this.chunkKey = chunkKey;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private static Record load(CompoundTag tag) {
            long key = tag.getLong("key");
            Record record = new Record(key, unpackChunkX(key), unpackChunkZ(key));
            record.transformVersion = tag.getInt("version");
            record.targetDay = tag.getInt("day");
            record.targetProgress = tag.getFloat("progress");
            record.targetPhase = tag.getInt("phase");
            record.passIndex = tag.getInt("pass");
            record.cursor = tag.getInt("cursor");
            record.complete = tag.getBoolean("complete");
            record.updatedGameTime = tag.getLong("updatedGameTime");
            return record;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("key", chunkKey);
            tag.putInt("version", transformVersion);
            tag.putInt("day", targetDay);
            tag.putFloat("progress", targetProgress);
            tag.putInt("phase", targetPhase);
            tag.putInt("pass", passIndex);
            tag.putInt("cursor", cursor);
            tag.putBoolean("complete", complete);
            tag.putLong("updatedGameTime", updatedGameTime);
            return tag;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public int transformVersion() {
            return transformVersion;
        }

        public int targetDay() {
            return targetDay;
        }

        public int targetPhase() {
            return targetPhase;
        }

        public float targetProgress() {
            return targetProgress;
        }

        public int passIndex() {
            return passIndex;
        }

        public int cursor() {
            return cursor;
        }

        public boolean complete() {
            return complete;
        }

        public void begin(int transformVersion, int targetDay, int targetPhase, float targetProgress) {
            this.transformVersion = transformVersion;
            this.targetDay = targetDay;
            this.targetPhase = targetPhase;
            this.targetProgress = targetProgress;
            this.passIndex = 0;
            this.cursor = 0;
            this.complete = false;
        }

        public void advance(int passIndex, int cursor, long gameTime) {
            this.passIndex = passIndex;
            this.cursor = cursor;
            this.updatedGameTime = gameTime;
        }

        public void complete(int transformVersion, int targetDay, int targetPhase, float targetProgress, long gameTime) {
            this.transformVersion = transformVersion;
            this.targetDay = targetDay;
            this.targetPhase = targetPhase;
            this.targetProgress = targetProgress;
            this.passIndex = 0;
            this.cursor = 0;
            this.complete = true;
            this.updatedGameTime = gameTime;
        }
    }
}
