package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RemnantState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World authority for Remnant shelters and their reversible owned geometry. */
public final class RemnantLureSavedData extends SavedData {
    public static final int CURRENT_VERSION = 5;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_remnant_lures";

    private final Map<UUID, LureRecord> lures = new LinkedHashMap<>();
    private final Map<Long, Long> regionCooldowns = new LinkedHashMap<>();

    public static RemnantLureSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RemnantLureSavedData::new,
                        RemnantLureSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public static RemnantLureSavedData load(CompoundTag tag,
                                             HolderLookup.Provider registries) {
        RemnantLureSavedData data = new RemnantLureSavedData();
        for (Tag raw : tag.getList("lures", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag value) || !value.hasUUID("id")
                    || !value.contains("origin", Tag.TAG_LONG)) continue;
            LureRecord record = new LureRecord(value.getUUID("id"),
                    value.getLong("region"), value.getString("template"),
                    BlockPos.of(value.getLong("origin")), value.getInt("rotation"),
                    value.getLong("seed"));
            record.entityId = value.hasUUID("entity") ? value.getUUID("entity") : null;
            record.state = RemnantState.byOrdinal(value.getInt("state"));
            if (record.state == RemnantState.DYING) record.state = RemnantState.COLLAPSING;
            else if (record.state.isUnsafeAfterReload()) record.state = RemnantState.HUNTING;
            record.stateTicks = Math.max(0, value.getInt("stateTicks"));
            record.collapseCursor = Math.max(0, value.getInt("collapseCursor"));
            record.committedPlayer = value.hasUUID("committedPlayer")
                    ? value.getUUID("committedPlayer") : null;
            record.falseOpeningUsed = value.getBoolean("falseOpeningUsed");
            record.roomShiftUsed = value.getBoolean("roomShiftUsed");
            record.shellReconciled = value.getBoolean("shellReconciled");
            record.radioSequenceTicks = value.contains("radioSequenceTicks", Tag.TAG_INT)
                    ? value.getInt("radioSequenceTicks") : -1;
            record.radioLine = value.contains("radioLine", Tag.TAG_INT)
                    ? value.getInt("radioLine") : -1;
            record.radioBroadcastCount = value.contains("radioBroadcastCount", Tag.TAG_INT)
                    ? Math.max(0, value.getInt("radioBroadcastCount"))
                    : value.getBoolean("radioSpoken") ? 1 : 0;
            record.radioCooldownTicks = value.contains("radioCooldownTicks", Tag.TAG_INT)
                    ? Math.max(0, value.getInt("radioCooldownTicks")) : 0;
            readPositions(value, "triggers", record.triggers);
            readPositions(value, "seams", record.seams);
            readPositions(value, "anchors", record.wallAnchors);
            readPositions(value, "owned", record.ownedPositions);
            readPositions(value, "membrane", record.membranePositions);
            readPositions(value, "rubble", record.rubblePositions);
            readPositions(value, "foundation", record.foundationPositions);
            data.lures.put(record.id, record);
        }
        for (Tag raw : tag.getList("regionCooldowns", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag value) {
                data.regionCooldowns.put(value.getLong("region"), value.getLong("until"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        ListTag lureList = new ListTag();
        for (LureRecord record : lures.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", record.id);
            value.putLong("region", record.regionKey);
            value.putString("template", record.templateId);
            value.putLong("origin", record.origin.asLong());
            value.putInt("rotation", record.rotation);
            value.putLong("seed", record.layoutSeed);
            if (record.entityId != null) value.putUUID("entity", record.entityId);
            value.putInt("state", record.state.ordinal());
            value.putInt("stateTicks", record.stateTicks);
            value.putInt("collapseCursor", record.collapseCursor);
            if (record.committedPlayer != null) {
                value.putUUID("committedPlayer", record.committedPlayer);
            }
            value.putBoolean("falseOpeningUsed", record.falseOpeningUsed);
            value.putBoolean("roomShiftUsed", record.roomShiftUsed);
            value.putBoolean("shellReconciled", record.shellReconciled);
            value.putInt("radioSequenceTicks", record.radioSequenceTicks);
            value.putInt("radioLine", record.radioLine);
            value.putInt("radioBroadcastCount", record.radioBroadcastCount);
            value.putInt("radioCooldownTicks", record.radioCooldownTicks);
            writePositions(value, "triggers", record.triggers);
            writePositions(value, "seams", record.seams);
            writePositions(value, "anchors", record.wallAnchors);
            writePositions(value, "owned", record.ownedPositions);
            writePositions(value, "membrane", record.membranePositions);
            writePositions(value, "rubble", record.rubblePositions);
            writePositions(value, "foundation", record.foundationPositions);
            lureList.add(value);
        }
        tag.put("lures", lureList);
        ListTag cooldownList = new ListTag();
        regionCooldowns.forEach((region, until) -> {
            CompoundTag value = new CompoundTag();
            value.putLong("region", region);
            value.putLong("until", until);
            cooldownList.add(value);
        });
        tag.put("regionCooldowns", cooldownList);
        return tag;
    }

    public LureRecord create(long regionKey, String templateId, BlockPos origin,
                             int rotation, long seed) {
        LureRecord record = new LureRecord(UUID.randomUUID(), regionKey,
                templateId, origin.immutable(), rotation, seed);
        lures.put(record.id, record);
        setDirty();
        return record;
    }

    public Optional<LureRecord> lure(UUID id) {
        return Optional.ofNullable(lures.get(id));
    }

    public Collection<LureRecord> lures() {
        return List.copyOf(lures.values());
    }

    public Optional<LureRecord> unresolvedInRegion(long regionKey) {
        return lures.values().stream().filter(record -> record.regionKey == regionKey
                && record.state != RemnantState.RESOLVED).findFirst();
    }

    public Optional<LureRecord> at(BlockPos pos) {
        return lures.values().stream().filter(record -> record.contains(pos)).findFirst();
    }

    public boolean protectsFromEnvironmentalMutation(BlockPos pos) {
        return lures.values().stream().anyMatch(record ->
                record.state.protectsShelterFromEnvironment()
                        && record.contains(pos)
                        && record.ownsAuthoredBlock(pos));
    }

    public long cooldown(long regionKey) {
        return regionCooldowns.getOrDefault(regionKey, 0L);
    }

    public void resolve(LureRecord record, long nextTime) {
        record.state = RemnantState.RESOLVED;
        record.entityId = null;
        regionCooldowns.put(record.regionKey, nextTime);
        setDirty();
    }

    public void remove(UUID id) {
        if (lures.remove(id) != null) setDirty();
    }

    public void changed() {
        setDirty();
    }

    private static void readPositions(CompoundTag tag, String key, List<BlockPos> output) {
        for (long packed : tag.getLongArray(key)) output.add(BlockPos.of(packed));
    }

    private static void writePositions(CompoundTag tag, String key, List<BlockPos> positions) {
        tag.putLongArray(key, positions.stream().mapToLong(BlockPos::asLong).toArray());
    }

    public static final class LureRecord {
        private final UUID id;
        private final long regionKey;
        private final String templateId;
        private final BlockPos origin;
        private final int rotation;
        private final long layoutSeed;
        private UUID entityId;
        private RemnantState state = RemnantState.PLACING;
        private int stateTicks;
        private int collapseCursor;
        private UUID committedPlayer;
        private boolean falseOpeningUsed;
        private boolean roomShiftUsed;
        private boolean shellReconciled;
        private int radioSequenceTicks = -1;
        private int radioLine = -1;
        private int radioBroadcastCount;
        private int radioCooldownTicks;
        private final List<BlockPos> triggers = new ArrayList<>();
        private final List<BlockPos> seams = new ArrayList<>();
        private final List<BlockPos> wallAnchors = new ArrayList<>();
        private final List<BlockPos> ownedPositions = new ArrayList<>();
        private final List<BlockPos> membranePositions = new ArrayList<>();
        private final List<BlockPos> rubblePositions = new ArrayList<>();
        private final List<BlockPos> foundationPositions = new ArrayList<>();

        private LureRecord(UUID id, long regionKey, String templateId,
                           BlockPos origin, int rotation, long layoutSeed) {
            this.id = id;
            this.regionKey = regionKey;
            this.templateId = templateId;
            this.origin = origin;
            this.rotation = rotation;
            this.layoutSeed = layoutSeed;
        }

        public UUID id() { return id; }
        public long regionKey() { return regionKey; }
        public String templateId() { return templateId; }
        public BlockPos origin() { return origin; }
        public int rotation() { return rotation; }
        public long layoutSeed() { return layoutSeed; }
        public Optional<UUID> entityId() { return Optional.ofNullable(entityId); }
        public void bindEntity(UUID id) { entityId = id; }
        public RemnantState state() { return state; }
        public void setState(RemnantState next) { state = next; stateTicks = 0; }
        public int stateTicks() { return stateTicks; }
        public void tickState() { stateTicks++; }
        public int collapseCursor() { return collapseCursor; }
        public void advanceCollapse(int count) { collapseCursor += count; }
        public Optional<UUID> committedPlayer() { return Optional.ofNullable(committedPlayer); }
        public void commit(UUID player) { committedPlayer = player; setState(RemnantState.COMMITTED); }
        public boolean falseOpeningUsed() { return falseOpeningUsed; }
        public void markFalseOpeningUsed() { falseOpeningUsed = true; }
        public boolean roomShiftUsed() { return roomShiftUsed; }
        public void markRoomShiftUsed() { roomShiftUsed = true; }
        public boolean shellReconciled() { return shellReconciled; }
        public void markShellReconciled() { shellReconciled = true; }
        public int radioSequenceTicks() { return radioSequenceTicks; }
        public int radioLine() { return radioLine; }
        public int radioBroadcastCount() { return radioBroadcastCount; }
        public int radioCooldownTicks() { return radioCooldownTicks; }
        public void startRadioSequence(int line) {
            radioLine = line;
            radioSequenceTicks = 0;
        }
        public void advanceRadioSequence() { radioSequenceTicks++; }
        public void tickRadioCooldown() {
            if (radioCooldownTicks > 0) radioCooldownTicks--;
        }
        public void finishRadioSequence(int repeatDelay) {
            radioBroadcastCount++;
            radioSequenceTicks = -1;
            radioLine = -1;
            radioCooldownTicks = Math.max(0, repeatDelay);
        }
        public void cancelRadioSequence() {
            radioSequenceTicks = -1;
            radioLine = -1;
            radioCooldownTicks = 0;
        }
        public List<BlockPos> triggers() { return triggers; }
        public List<BlockPos> seams() { return seams; }
        public List<BlockPos> wallAnchors() { return wallAnchors; }
        public List<BlockPos> ownedPositions() { return ownedPositions; }
        public List<BlockPos> membranePositions() { return membranePositions; }
        public List<BlockPos> rubblePositions() { return rubblePositions; }
        public List<BlockPos> foundationPositions() { return foundationPositions; }

        private boolean ownsAuthoredBlock(BlockPos pos) {
            return triggers.contains(pos) || seams.contains(pos)
                    || ownedPositions.contains(pos) || membranePositions.contains(pos)
                    || rubblePositions.contains(pos) || foundationPositions.contains(pos);
        }

        public boolean contains(BlockPos pos) {
            int dx = Math.abs(pos.getX() - origin.getX());
            int dz = Math.abs(pos.getZ() - origin.getZ());
            int dy = pos.getY() - origin.getY();
            return dx <= 8 && dz <= 8 && dy >= -2 && dy <= 10;
        }
    }
}
