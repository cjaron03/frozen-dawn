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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent loaded-time authority for the post-Maeve Bloom. */
public final class BloomSavedData extends SavedData {
    public static final int CURRENT_VERSION = 2;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_bloom";

    private final Map<UUID, HearthGrowth> hearthGrowth = new LinkedHashMap<>();
    private final Map<ChunkGrowthKey, ChunkGrowth> chunkGrowth = new LinkedHashMap<>();
    private final Map<Long, Long> sealedContactTicks = new LinkedHashMap<>();
    private int debugRadius = -1;

    public static BloomSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BloomSavedData::new, BloomSavedData::load,
                        DataFixTypes.LEVEL), DATA_NAME);
    }

    public static BloomSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BloomSavedData data = new BloomSavedData();
        data.debugRadius = tag.contains("debugRadius", Tag.TAG_INT)
                ? tag.getInt("debugRadius") : -1;
        for (Tag entry : tag.getList("hearthGrowth", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag value && value.hasUUID("hearthId")) {
                UUID id = value.getUUID("hearthId");
                data.hearthGrowth.put(id, data.new HearthGrowth(
                        id, Math.max(0L, value.getLong("activeTicks")),
                        value.getBoolean("seeded")));
            }
        }
        for (Tag entry : tag.getList("chunkGrowth", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag value && value.hasUUID("hearthId")) {
                UUID id = value.getUUID("hearthId");
                long chunkPos = value.getLong("chunkPos");
                ChunkGrowthKey key = new ChunkGrowthKey(id, chunkPos);
                data.chunkGrowth.put(key, data.new ChunkGrowth(
                        key,
                        value.getBoolean("seedAttempted"),
                        Math.max(0, value.getInt("cursor")),
                        value.getBoolean("complete"),
                        Math.max(0, value.getInt("passEdits")),
                        value.contains("processedBand", Tag.TAG_INT)
                                ? value.getInt("processedBand") : -1,
                        Math.max(0, value.getInt("processedOverlap"))));
            }
        }
        for (Tag entry : tag.getList("sealedWear", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag value) {
                data.sealedContactTicks.put(value.getLong("pos"),
                        Math.max(0L, value.getLong("ticks")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        tag.putInt("debugRadius", debugRadius);
        ListTag hearthList = new ListTag();
        for (HearthGrowth growth : hearthGrowth.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("hearthId", growth.hearthId);
            value.putLong("activeTicks", growth.activeTicks);
            value.putBoolean("seeded", growth.seeded);
            hearthList.add(value);
        }
        tag.put("hearthGrowth", hearthList);

        ListTag chunkList = new ListTag();
        for (ChunkGrowth growth : chunkGrowth.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("hearthId", growth.key.hearthId);
            value.putLong("chunkPos", growth.key.chunkPos);
            value.putBoolean("seedAttempted", growth.seedAttempted);
            value.putInt("cursor", growth.cursor);
            value.putBoolean("complete", growth.complete);
            value.putInt("passEdits", growth.passEdits);
            value.putInt("processedBand", growth.processedBand);
            value.putInt("processedOverlap", growth.processedOverlap);
            chunkList.add(value);
        }
        tag.put("chunkGrowth", chunkList);

        ListTag wearList = new ListTag();
        for (Map.Entry<Long, Long> entry : sealedContactTicks.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", entry.getKey());
            value.putLong("ticks", entry.getValue());
            wearList.add(value);
        }
        tag.put("sealedWear", wearList);
        return tag;
    }

    public HearthGrowth hearth(UUID id) {
        return hearthGrowth.computeIfAbsent(id, HearthGrowth::new);
    }

    public ChunkGrowth chunk(UUID hearthId, long chunkPos) {
        ChunkGrowthKey key = new ChunkGrowthKey(hearthId, chunkPos);
        return chunkGrowth.computeIfAbsent(key, ChunkGrowth::new);
    }

    public Collection<ChunkGrowth> chunks() {
        return chunkGrowth.values();
    }

    public void reactivateChunksNear(net.minecraft.world.level.ChunkPos center) {
        for (ChunkGrowth growth : chunkGrowth.values()) {
            net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(
                    growth.key.chunkPos);
            if (Math.abs(chunk.x - center.x) <= 1 && Math.abs(chunk.z - center.z) <= 1) {
                growth.resetPass();
            }
        }
    }

    public int recordCount() {
        return chunkGrowth.size();
    }

    public long pendingCount() {
        return chunkGrowth.values().stream().filter(growth -> !growth.complete).count();
    }

    public int debugRadius() {
        return debugRadius;
    }

    public void setDebugRadius(int radius) {
        debugRadius = radius < 0 ? -1 : Math.min(1_000, radius);
        setDirty();
    }

    public long addSealedContact(BlockPos pos, long ticks) {
        long packed = pos.asLong();
        long next = Math.max(0L, sealedContactTicks.getOrDefault(packed, 0L) + ticks);
        sealedContactTicks.put(packed, next);
        setDirty();
        return next;
    }

    public void removeSealedContact(BlockPos pos) {
        if (sealedContactTicks.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public int sealedRecordCount() {
        return sealedContactTicks.size();
    }

    public void resetAuthority() {
        hearthGrowth.clear();
        chunkGrowth.clear();
        sealedContactTicks.clear();
        debugRadius = -1;
        setDirty();
    }

    public final class HearthGrowth {
        private final UUID hearthId;
        private long activeTicks;
        private boolean seeded;

        private HearthGrowth(UUID hearthId) {
            this(hearthId, 0L, false);
        }

        private HearthGrowth(UUID hearthId, long activeTicks, boolean seeded) {
            this.hearthId = hearthId;
            this.activeTicks = activeTicks;
            this.seeded = seeded;
        }

        public long activeTicks() {
            return activeTicks;
        }

        public void advance(long ticks) {
            if (ticks > 0L) {
                activeTicks = Math.min(Long.MAX_VALUE, activeTicks + ticks);
                BloomSavedData.this.setDirty();
            }
        }

        public void setActiveTicks(long ticks) {
            activeTicks = Math.max(0L, ticks);
            BloomSavedData.this.setDirty();
        }

        public boolean seeded() {
            return seeded;
        }

        public void markSeeded() {
            if (!seeded) {
                seeded = true;
                BloomSavedData.this.setDirty();
            }
        }
    }

    public final class ChunkGrowth {
        private final ChunkGrowthKey key;
        private boolean seedAttempted;
        private int cursor;
        private boolean complete;
        private int passEdits;
        private int processedBand;
        private int processedOverlap;

        private ChunkGrowth(ChunkGrowthKey key) {
            this(key, false, 0, false, 0, -1, 0);
        }

        private ChunkGrowth(ChunkGrowthKey key, boolean seedAttempted, int cursor,
                            boolean complete, int passEdits, int processedBand,
                            int processedOverlap) {
            this.key = key;
            this.seedAttempted = seedAttempted;
            this.cursor = cursor;
            this.complete = complete;
            this.passEdits = passEdits;
            this.processedBand = processedBand;
            this.processedOverlap = processedOverlap;
        }

        public UUID hearthId() {
            return key.hearthId;
        }

        public long chunkPos() {
            return key.chunkPos;
        }

        public boolean seedAttempted() {
            return seedAttempted;
        }

        public int cursor() {
            return cursor;
        }

        public boolean complete() {
            return complete;
        }

        public void markSeedAttempted() {
            seedAttempted = true;
            BloomSavedData.this.setDirty();
        }

        public void setCursor(int cursor) {
            this.cursor = Math.max(0, cursor);
            BloomSavedData.this.setDirty();
        }

        public void recordEdits(int edits) {
            if (edits > 0) {
                passEdits += edits;
                BloomSavedData.this.setDirty();
            }
        }

        public void prepareFor(int band, int overlap) {
            if (complete && (band > processedBand || overlap > processedOverlap)) {
                resetPass();
            }
        }

        public void finishPass(int band, int overlap) {
            if (passEdits > 0) {
                cursor = 0;
                passEdits = 0;
                complete = false;
            } else {
                complete = true;
                processedBand = Math.max(processedBand, band);
                processedOverlap = Math.max(processedOverlap, overlap);
            }
            BloomSavedData.this.setDirty();
        }

        public void markComplete() {
            complete = true;
            passEdits = 0;
            BloomSavedData.this.setDirty();
        }

        public void resetPass() {
            cursor = 0;
            complete = false;
            passEdits = 0;
            BloomSavedData.this.setDirty();
        }
    }

    private record ChunkGrowthKey(UUID hearthId, long chunkPos) {
    }
}
