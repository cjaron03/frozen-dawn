package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.PostMaeveEncounterType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent encounter pressure keyed by player or loaded-region owner. */
public final class PostMaeveEncounterSavedData extends SavedData {
    public static final int CURRENT_VERSION = 2;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_post_maeve_encounters";

    private final Map<String, OwnerRecord> owners = new LinkedHashMap<>();

    public static PostMaeveEncounterSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PostMaeveEncounterSavedData::new,
                        PostMaeveEncounterSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static PostMaeveEncounterSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        PostMaeveEncounterSavedData data = new PostMaeveEncounterSavedData();
        for (Tag rawOwner : tag.getList("owners", Tag.TAG_COMPOUND)) {
            if (!(rawOwner instanceof CompoundTag ownerTag)) continue;
            String key = ownerTag.getString("key");
            if (key.isBlank()) continue;
            OwnerRecord owner = new OwnerRecord(key);
            owner.lastEncounterTick = ownerTag.contains("lastEncounterTick", Tag.TAG_LONG)
                    ? ownerTag.getLong("lastEncounterTick") : -1L;
            owner.lastType = parseType(ownerTag.getString("lastType"));
            for (Tag rawEntry : ownerTag.getList("entries", Tag.TAG_COMPOUND)) {
                if (!(rawEntry instanceof CompoundTag entryTag)) continue;
                PostMaeveEncounterType type = parseType(entryTag.getString("type"));
                if (type == null) continue;
                Entry entry = new Entry();
                entry.windowStartTick = entryTag.contains("windowStart", Tag.TAG_LONG)
                        ? entryTag.getLong("windowStart") : -1L;
                entry.lastSuccessTick = entryTag.contains("lastSuccess", Tag.TAG_LONG)
                        ? entryTag.getLong("lastSuccess") : -1L;
                entry.failedAttempts = Math.max(0, entryTag.getInt("failures"));
                entry.lastChance = Math.max(0.0D,
                        Math.min(1.0D, entryTag.getDouble("lastChance")));
                entry.lastReason = entryTag.getString("lastReason");
                entry.debugReady = entryTag.getBoolean("debugReady");
                owner.entries.put(type, entry);
            }
            data.owners.put(key, owner);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        ListTag ownerList = new ListTag();
        for (OwnerRecord owner : owners.values()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putString("key", owner.key);
            ownerTag.putLong("lastEncounterTick", owner.lastEncounterTick);
            if (owner.lastType != null) ownerTag.putString("lastType", owner.lastType.name());
            ListTag entries = new ListTag();
            for (Map.Entry<PostMaeveEncounterType, Entry> value
                    : owner.entries.entrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("type", value.getKey().name());
                Entry entry = value.getValue();
                entryTag.putLong("windowStart", entry.windowStartTick);
                entryTag.putLong("lastSuccess", entry.lastSuccessTick);
                entryTag.putInt("failures", entry.failedAttempts);
                entryTag.putDouble("lastChance", entry.lastChance);
                entryTag.putString("lastReason", entry.lastReason);
                entryTag.putBoolean("debugReady", entry.debugReady);
                entries.add(entryTag);
            }
            ownerTag.put("entries", entries);
            ownerList.add(ownerTag);
        }
        tag.put("owners", ownerList);
        return tag;
    }

    public OwnerRecord owner(String key) {
        return owners.computeIfAbsent(key, OwnerRecord::new);
    }

    public void changed() {
        setDirty();
    }

    private static PostMaeveEncounterType parseType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PostMaeveEncounterType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final class OwnerRecord {
        private final String key;
        private final EnumMap<PostMaeveEncounterType, Entry> entries =
                new EnumMap<>(PostMaeveEncounterType.class);
        private long lastEncounterTick = -1L;
        private PostMaeveEncounterType lastType;

        private OwnerRecord(String key) {
            this.key = key;
        }

        public Entry entry(PostMaeveEncounterType type) {
            return entries.computeIfAbsent(type, ignored -> new Entry());
        }

        public long lastEncounterTick() {
            return lastEncounterTick;
        }

        public PostMaeveEncounterType lastType() {
            return lastType;
        }

        public void recordSuccess(PostMaeveEncounterType type, long now) {
            lastEncounterTick = now;
            lastType = type;
        }

        public void clearSharedCooldown() {
            lastEncounterTick = -1L;
            lastType = null;
        }

        public void reset(PostMaeveEncounterType type) {
            entries.remove(type);
            clearSharedCooldown();
        }
    }

    public static final class Entry {
        private long windowStartTick = -1L;
        private long lastSuccessTick = -1L;
        private int failedAttempts;
        private double lastChance;
        private String lastReason = "never eligible";
        private boolean debugReady;

        public long windowStartTick() {
            return windowStartTick;
        }

        public long lastSuccessTick() {
            return lastSuccessTick;
        }

        public int failedAttempts() {
            return failedAttempts;
        }

        public double lastChance() {
            return lastChance;
        }

        public String lastReason() {
            return lastReason;
        }

        public boolean debugReady() {
            return debugReady;
        }

        public void begin(long now) {
            if (windowStartTick < 0L) windowStartTick = now;
        }

        public void recordRoll(double chance, boolean selected) {
            lastChance = chance;
            lastReason = selected ? "selected; validating spawn" : "chance roll missed";
            if (!selected) failedAttempts++;
        }

        public void recordBlocked(String reason) {
            failedAttempts++;
            lastReason = reason == null || reason.isBlank()
                    ? "spawn validation failed" : reason;
        }

        public void recordCooldown(String reason, double chance) {
            lastChance = chance;
            lastReason = reason;
        }

        public void recordSuccess(long now) {
            windowStartTick = now;
            lastSuccessTick = now;
            failedAttempts = 0;
            lastChance = 0.0D;
            lastReason = "spawned";
            debugReady = false;
        }

        public void markDebugReady(long now, long guaranteedIntervalTicks) {
            windowStartTick = Math.max(0L, now - guaranteedIntervalTicks);
            lastSuccessTick = -1L;
            lastChance = 1.0D;
            lastReason = "debug-ready; awaiting natural spawn validation";
            debugReady = true;
        }
    }
}
