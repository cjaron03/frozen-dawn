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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent loaded-time authority for the post-Maeve Bloom. */
public final class BloomSavedData extends SavedData {
    public static final int CURRENT_VERSION = 6;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_bloom";

    private final Map<UUID, HearthGrowth> hearthGrowth = new LinkedHashMap<>();
    private final Map<ChunkGrowthKey, ChunkGrowth> chunkGrowth = new LinkedHashMap<>();
    private final Map<Long, Long> sealedContactTicks = new LinkedHashMap<>();
    private final Map<UUID, SporeFront> sporeFronts = new LinkedHashMap<>();
    private int debugRadius = -1;
    private UUID firstEruptionHearthId;
    private BlockPos firstEruptionBase;
    private long firstEruptionStartGameTime = -1L;
    private boolean firstEruptionImpactPlayed;
    private boolean firstEruptionComplete;

    public static BloomSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BloomSavedData::new, BloomSavedData::load,
                        DataFixTypes.LEVEL), DATA_NAME);
    }

    public static BloomSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BloomSavedData data = new BloomSavedData();
        int storedVersion = tag.contains("dataVersion", Tag.TAG_INT)
                ? tag.getInt("dataVersion") : 0;
        data.debugRadius = tag.contains("debugRadius", Tag.TAG_INT)
                ? tag.getInt("debugRadius") : -1;
        data.firstEruptionHearthId = tag.hasUUID("firstEruptionHearthId")
                ? tag.getUUID("firstEruptionHearthId") : null;
        data.firstEruptionBase = tag.contains("firstEruptionBase", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("firstEruptionBase")) : null;
        data.firstEruptionStartGameTime = tag.contains(
                "firstEruptionStartGameTime", Tag.TAG_LONG)
                ? tag.getLong("firstEruptionStartGameTime") : -1L;
        data.firstEruptionImpactPlayed = tag.getBoolean("firstEruptionImpactPlayed");
        data.firstEruptionComplete = tag.getBoolean("firstEruptionComplete");
        for (Tag entry : tag.getList("hearthGrowth", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag value && value.hasUUID("hearthId")) {
                UUID id = value.getUUID("hearthId");
                data.hearthGrowth.put(id, data.new HearthGrowth(
                        id, Math.max(0L, value.getLong("activeTicks")),
                        value.getBoolean("seeded"),
                        Math.max(0, value.getInt("originRootCursor")),
                        value.getBoolean("originRootFormed"),
                        value.contains("originRootBase", Tag.TAG_LONG)
                                ? BlockPos.of(value.getLong("originRootBase")) : null));
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
        for (Tag entry : tag.getList("sporeFronts", Tag.TAG_COMPOUND)) {
            if (!(entry instanceof CompoundTag value) || !value.hasUUID("id")
                    || !value.hasUUID("lineageId") || !value.contains("anchor", Tag.TAG_LONG)) {
                continue;
            }
            UUID id = value.getUUID("id");
            SporeFront front = data.new SporeFront(
                    id,
                    value.getUUID("lineageId"),
                    BlockPos.of(value.getLong("anchor")),
                    value.getBoolean("satellite"),
                    value.hasUUID("activeSporeId") ? value.getUUID("activeSporeId") : null,
                    value.contains("activeSporePos", Tag.TAG_LONG)
                            ? BlockPos.of(value.getLong("activeSporePos")) : null,
                    Math.max(0L, value.getLong("loadedTicks")),
                    Math.max(0, value.getInt("growthCursor")),
                    Math.max(0, value.getInt("maintenanceCursor")),
                    Math.max(0L, value.getLong("maintenanceTicks")),
                    value.getBoolean("relayEmitted"),
                    value.hasUUID("corpseId") ? value.getUUID("corpseId") : null,
                    value.getBoolean("removed"),
                    Math.max(0.0D, value.getDouble("sourceEdgeRadius")),
                    Math.max(0, value.getInt("initialPatchEdits")));
            if (value.hasUUID("secondarySporeId")) {
                front.secondarySporeId = value.getUUID("secondarySporeId");
                front.secondarySporePos = value.contains("secondarySporePos", Tag.TAG_LONG)
                        ? BlockPos.of(value.getLong("secondarySporePos")) : front.anchor;
            }
            data.sporeFronts.put(id, front);
        }
        if (storedVersion < 5 && data.hearthGrowth.values().stream()
                .anyMatch(HearthGrowth::seeded)) {
            data.firstEruptionImpactPlayed = true;
            data.firstEruptionComplete = true;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        tag.putInt("debugRadius", debugRadius);
        if (firstEruptionHearthId != null) {
            tag.putUUID("firstEruptionHearthId", firstEruptionHearthId);
        }
        if (firstEruptionBase != null) {
            tag.putLong("firstEruptionBase", firstEruptionBase.asLong());
        }
        tag.putLong("firstEruptionStartGameTime", firstEruptionStartGameTime);
        tag.putBoolean("firstEruptionImpactPlayed", firstEruptionImpactPlayed);
        tag.putBoolean("firstEruptionComplete", firstEruptionComplete);
        ListTag hearthList = new ListTag();
        for (HearthGrowth growth : hearthGrowth.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("hearthId", growth.hearthId);
            value.putLong("activeTicks", growth.activeTicks);
            value.putBoolean("seeded", growth.seeded);
            value.putInt("originRootCursor", growth.originRootCursor);
            value.putBoolean("originRootFormed", growth.originRootFormed);
            if (growth.originRootBase != null) {
                value.putLong("originRootBase", growth.originRootBase.asLong());
            }
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

        ListTag sporeList = new ListTag();
        for (SporeFront front : sporeFronts.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", front.id);
            value.putUUID("lineageId", front.lineageId);
            value.putLong("anchor", front.anchor.asLong());
            value.putBoolean("satellite", front.satellite);
            if (front.activeSporeId != null) {
                value.putUUID("activeSporeId", front.activeSporeId);
            }
            if (front.activeSporePos != null) {
                value.putLong("activeSporePos", front.activeSporePos.asLong());
            }
            if (front.secondarySporeId != null) {
                value.putUUID("secondarySporeId", front.secondarySporeId);
            }
            if (front.secondarySporePos != null) {
                value.putLong("secondarySporePos", front.secondarySporePos.asLong());
            }
            value.putLong("loadedTicks", front.loadedTicks);
            value.putInt("growthCursor", front.growthCursor);
            value.putInt("maintenanceCursor", front.maintenanceCursor);
            value.putLong("maintenanceTicks", front.maintenanceTicks);
            value.putBoolean("relayEmitted", front.relayEmitted);
            if (front.corpseId != null) {
                value.putUUID("corpseId", front.corpseId);
            }
            value.putBoolean("removed", front.removed);
            value.putDouble("sourceEdgeRadius", front.sourceEdgeRadius);
            value.putInt("initialPatchEdits", front.initialPatchEdits);
            sporeList.add(value);
        }
        tag.put("sporeFronts", sporeList);
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

    public boolean firstEruptionStarted() {
        return firstEruptionStartGameTime >= 0L;
    }

    public Optional<UUID> firstEruptionHearthId() {
        return Optional.ofNullable(firstEruptionHearthId);
    }

    public Optional<BlockPos> firstEruptionBase() {
        return Optional.ofNullable(firstEruptionBase);
    }

    public long firstEruptionStartGameTime() {
        return firstEruptionStartGameTime;
    }

    public boolean firstEruptionImpactPlayed() {
        return firstEruptionImpactPlayed;
    }

    public boolean firstEruptionComplete() {
        return firstEruptionComplete;
    }

    public boolean startFirstEruption(UUID hearthId, BlockPos base, long gameTime) {
        if (firstEruptionStarted() || firstEruptionComplete
                || hearthId == null || base == null) {
            return false;
        }
        firstEruptionHearthId = hearthId;
        firstEruptionBase = base.immutable();
        firstEruptionStartGameTime = Math.max(0L, gameTime);
        firstEruptionImpactPlayed = false;
        setDirty();
        return true;
    }

    public boolean markFirstEruptionImpactPlayed() {
        if (!firstEruptionStarted() || firstEruptionImpactPlayed) {
            return false;
        }
        firstEruptionImpactPlayed = true;
        setDirty();
        return true;
    }

    public boolean completeFirstEruption() {
        if (firstEruptionComplete) {
            return false;
        }
        firstEruptionImpactPlayed = true;
        firstEruptionComplete = true;
        setDirty();
        return true;
    }

    public void resetFirstEruption() {
        firstEruptionHearthId = null;
        firstEruptionBase = null;
        firstEruptionStartGameTime = -1L;
        firstEruptionImpactPlayed = false;
        firstEruptionComplete = false;
        setDirty();
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

    public SporeFront sporeFront(UUID id, UUID lineageId, BlockPos anchor,
                                 boolean satellite, double sourceEdgeRadius) {
        SporeFront front = sporeFronts.computeIfAbsent(id,
                ignored -> new SporeFront(id, lineageId, anchor, satellite,
                        sourceEdgeRadius));
        front.updateAnchor(anchor);
        front.updateSourceEdgeRadius(sourceEdgeRadius);
        return front;
    }

    public Optional<SporeFront> sporeFront(UUID id) {
        return Optional.ofNullable(sporeFronts.get(id));
    }

    public Collection<SporeFront> sporeFronts() {
        return sporeFronts.values();
    }

    public void removeSporeFront(UUID id) {
        if (sporeFronts.remove(id) != null) {
            setDirty();
        }
    }

    public long activeSporeCount() {
        return sporeFronts.values().stream()
                .filter(front -> !front.removed)
                .mapToLong(SporeFront::activeSporeCount)
                .sum();
    }

    public void resetAuthority() {
        hearthGrowth.clear();
        chunkGrowth.clear();
        sealedContactTicks.clear();
        sporeFronts.clear();
        debugRadius = -1;
        resetFirstEruption();
        setDirty();
    }

    /** Restarts ordinary Hearth-driven growth without disturbing barriers or relay nodes. */
    public void restartGrowthAuthorityForDebug() {
        hearthGrowth.clear();
        chunkGrowth.clear();
        debugRadius = -1;
        resetFirstEruption();
        setDirty();
    }

    /** Lets a real Maeve erasure restart a world paused by the debug purge command. */
    public boolean resumePurgedGrowthForMaeveSequence() {
        if (debugRadius != 0) {
            return false;
        }
        restartGrowthAuthorityForDebug();
        return true;
    }

    public final class SporeFront {
        private final UUID id;
        private final UUID lineageId;
        private BlockPos anchor;
        private final boolean satellite;
        private UUID activeSporeId;
        private BlockPos activeSporePos;
        private UUID secondarySporeId;
        private BlockPos secondarySporePos;
        private long loadedTicks;
        private int growthCursor;
        private int maintenanceCursor;
        private long maintenanceTicks;
        private boolean relayEmitted;
        private UUID corpseId;
        private boolean removed;
        private double sourceEdgeRadius;
        private int initialPatchEdits;

        private SporeFront(UUID id, UUID lineageId, BlockPos anchor, boolean satellite,
                           double sourceEdgeRadius) {
            this(id, lineageId, anchor, satellite, null, null,
                    0L, 0, 0, 0L, false, null, false, sourceEdgeRadius, 0);
        }

        private SporeFront(UUID id, UUID lineageId, BlockPos anchor, boolean satellite,
                           UUID activeSporeId, BlockPos activeSporePos, long loadedTicks,
                           int growthCursor, int maintenanceCursor, long maintenanceTicks,
                           boolean relayEmitted, UUID corpseId, boolean removed) {
            this(id, lineageId, anchor, satellite, activeSporeId, activeSporePos,
                    loadedTicks, growthCursor, maintenanceCursor, maintenanceTicks,
                    relayEmitted, corpseId, removed, 0.0D, 0);
        }

        private SporeFront(UUID id, UUID lineageId, BlockPos anchor, boolean satellite,
                           UUID activeSporeId, BlockPos activeSporePos, long loadedTicks,
                           int growthCursor, int maintenanceCursor, long maintenanceTicks,
                           boolean relayEmitted, UUID corpseId, boolean removed,
                           double sourceEdgeRadius, int initialPatchEdits) {
            this.id = id;
            this.lineageId = lineageId;
            this.anchor = anchor.immutable();
            this.satellite = satellite;
            this.activeSporeId = activeSporeId;
            this.activeSporePos = activeSporePos == null ? null : activeSporePos.immutable();
            this.loadedTicks = loadedTicks;
            this.growthCursor = growthCursor;
            this.maintenanceCursor = maintenanceCursor;
            this.maintenanceTicks = maintenanceTicks;
            this.relayEmitted = relayEmitted;
            this.corpseId = corpseId;
            this.removed = removed;
            this.sourceEdgeRadius = sourceEdgeRadius;
            this.initialPatchEdits = initialPatchEdits;
        }

        public UUID id() {
            return id;
        }

        public UUID lineageId() {
            return lineageId;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public boolean satellite() {
            return satellite;
        }

        public Optional<UUID> activeSporeId() {
            return Optional.ofNullable(activeSporeId);
        }

        public Optional<BlockPos> activeSporePos() {
            return Optional.ofNullable(activeSporePos);
        }

        public List<UUID> activeSporeIds() {
            if (activeSporeId == null) {
                return List.of();
            }
            return secondarySporeId == null
                    ? List.of(activeSporeId)
                    : List.of(activeSporeId, secondarySporeId);
        }

        public Optional<BlockPos> activeSporePos(UUID entityId) {
            if (entityId.equals(activeSporeId)) {
                return Optional.ofNullable(activeSporePos);
            }
            if (entityId.equals(secondarySporeId)) {
                return Optional.ofNullable(secondarySporePos);
            }
            return Optional.empty();
        }

        public int activeSporeCount() {
            return (activeSporeId == null ? 0 : 1)
                    + (secondarySporeId == null ? 0 : 1);
        }

        public long loadedTicks() {
            return loadedTicks;
        }

        public int growthCursor() {
            return growthCursor;
        }

        public int maintenanceCursor() {
            return maintenanceCursor;
        }

        public boolean relayEmitted() {
            return relayEmitted;
        }

        public Optional<UUID> corpseId() {
            return Optional.ofNullable(corpseId);
        }

        public boolean removed() {
            return removed;
        }

        public double sourceEdgeRadius() {
            return sourceEdgeRadius;
        }

        public int initialPatchEdits() {
            return initialPatchEdits;
        }

        public void updateAnchor(BlockPos value) {
            if (!anchor.equals(value)) {
                anchor = value.immutable();
                BloomSavedData.this.setDirty();
            }
        }

        public void updateSourceEdgeRadius(double value) {
            double clamped = Math.max(0.0D, value);
            if (Math.abs(sourceEdgeRadius - clamped) > 0.001D) {
                sourceEdgeRadius = clamped;
                BloomSavedData.this.setDirty();
            }
        }

        public void bindSpore(UUID entityId, BlockPos pos) {
            if (entityId.equals(activeSporeId)) {
                activeSporePos = pos.immutable();
            } else if (entityId.equals(secondarySporeId)) {
                secondarySporePos = pos.immutable();
            } else if (activeSporeId == null) {
                activeSporeId = entityId;
                activeSporePos = pos.immutable();
            } else if (secondarySporeId == null) {
                secondarySporeId = entityId;
                secondarySporePos = pos.immutable();
            } else {
                return;
            }
            BloomSavedData.this.setDirty();
        }

        public void updateSporePos(UUID entityId, BlockPos pos) {
            if (entityId.equals(activeSporeId) && !pos.equals(activeSporePos)) {
                activeSporePos = pos.immutable();
                BloomSavedData.this.setDirty();
            } else if (entityId.equals(secondarySporeId)
                    && !pos.equals(secondarySporePos)) {
                secondarySporePos = pos.immutable();
                BloomSavedData.this.setDirty();
            }
        }

        public void clearSpore(UUID entityId) {
            if (entityId.equals(activeSporeId)) {
                activeSporeId = secondarySporeId;
                activeSporePos = secondarySporePos;
                secondarySporeId = null;
                secondarySporePos = null;
                BloomSavedData.this.setDirty();
            } else if (entityId.equals(secondarySporeId)) {
                secondarySporeId = null;
                secondarySporePos = null;
                BloomSavedData.this.setDirty();
            }
        }

        public void advanceLoaded(long ticks) {
            if (ticks > 0L && !removed) {
                loadedTicks = Math.min(Long.MAX_VALUE, loadedTicks + ticks);
                maintenanceTicks = Math.min(Long.MAX_VALUE, maintenanceTicks + ticks);
                BloomSavedData.this.setDirty();
            }
        }

        public void setGrowthCursor(int cursor) {
            growthCursor = Math.max(0, cursor);
            BloomSavedData.this.setDirty();
        }

        public void recordInitialPatchEdit() {
            initialPatchEdits = Math.min(3, initialPatchEdits + 1);
            BloomSavedData.this.setDirty();
        }

        public void setInitialPatchEdits(int edits) {
            initialPatchEdits = Math.max(0, Math.min(3, edits));
            BloomSavedData.this.setDirty();
        }

        public void setMaintenanceCursor(int cursor) {
            maintenanceCursor = Math.max(0, cursor);
            BloomSavedData.this.setDirty();
        }

        public long maintenanceTicks() {
            return maintenanceTicks;
        }

        public void resetMaintenanceTicks() {
            maintenanceTicks = 0L;
            maintenanceCursor = 0;
            BloomSavedData.this.setDirty();
        }

        public void markRelayEmitted() {
            if (!relayEmitted) {
                relayEmitted = true;
                BloomSavedData.this.setDirty();
            }
        }

        public void bindCorpse(UUID entityId) {
            corpseId = entityId;
            BloomSavedData.this.setDirty();
        }

        public void markRemoved() {
            removed = true;
            activeSporeId = null;
            activeSporePos = null;
            secondarySporeId = null;
            secondarySporePos = null;
            BloomSavedData.this.setDirty();
        }
    }

    public final class HearthGrowth {
        private final UUID hearthId;
        private long activeTicks;
        private boolean seeded;
        private int originRootCursor;
        private boolean originRootFormed;
        private BlockPos originRootBase;

        private HearthGrowth(UUID hearthId) {
            this(hearthId, 0L, false, 0, false, null);
        }

        private HearthGrowth(UUID hearthId, long activeTicks, boolean seeded,
                             int originRootCursor, boolean originRootFormed,
                             BlockPos originRootBase) {
            this.hearthId = hearthId;
            this.activeTicks = activeTicks;
            this.seeded = seeded;
            this.originRootCursor = originRootCursor;
            this.originRootFormed = originRootFormed;
            this.originRootBase = originRootBase;
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

        public int originRootCursor() {
            return originRootCursor;
        }

        public void setOriginRootCursor(int cursor) {
            int next = Math.max(0, cursor);
            if (originRootCursor != next) {
                originRootCursor = next;
                BloomSavedData.this.setDirty();
            }
        }

        public boolean originRootFormed() {
            return originRootFormed;
        }

        public Optional<BlockPos> originRootBase() {
            return Optional.ofNullable(originRootBase);
        }

        public void rememberOriginRootBase(BlockPos base) {
            if (originRootBase == null && base != null) {
                originRootBase = base.immutable();
                BloomSavedData.this.setDirty();
            }
        }

        public void markOriginRootFormed() {
            if (!originRootFormed) {
                originRootFormed = true;
                BloomSavedData.this.setDirty();
            }
        }

        public void resetOriginRootForDebug() {
            originRootCursor = 0;
            originRootFormed = false;
            originRootBase = null;
            BloomSavedData.this.setDirty();
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
