package com.frozendawn.aggregate;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** World-global authority for the once-per-world Aggregate lifecycle. */
public final class AggregateSavedData extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_aggregate";
    private static final int CURRENT_VERSION = 2;

    private double convergencePressure;
    private final EnumMap<AggregateLineage, Double> lineagePressure =
            AggregatePressurePolicy.emptyLineages();
    private AggregateStage stage = AggregateStage.DORMANT;
    private long lastStageAdvanceDay = -1L;
    private BlockPos ossuaryPos;
    private long ossuarySeed;
    private boolean awakeningEligible;
    private boolean fightStarted;
    private boolean aggregateResolved;
    private UUID activeAggregateId;
    private int participantCountSnapshot;
    private double overfeedPressure;
    private final List<AggregateLineage> lockedTraits = new ArrayList<>();
    private AggregateLineage dominantTrait;
    private float fightHealth;
    private float fightMaxHealth;
    private BlockPos fightPosition;
    private AggregatePhase fightPhase = AggregatePhase.AWAKENING;
    private boolean coreRewardGranted;
    private int missingEntityChecks;
    private final Set<Long> ossuaryBlocks = new LinkedHashSet<>();
    private final Set<Long> temporaryBlocks = new LinkedHashSet<>();
    private BlockPos stillpointPos;
    private ResourceLocation stillpointDimension;
    private UUID fightId;
    private int dischargeMask;
    private final List<ReinforcementRecord> reinforcements = new ArrayList<>();

    public static AggregateSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AggregateSavedData::new,
                        AggregateSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public static AggregateSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        AggregateSavedData data = new AggregateSavedData();
        data.convergencePressure = Math.max(0.0D, tag.getDouble("pressure"));
        for (AggregateLineage lineage : AggregateLineage.values()) {
            data.lineagePressure.put(lineage, Math.max(0.0D,
                    tag.getDouble("lineage_" + lineage.name().toLowerCase())));
        }
        data.stage = safeEnum(AggregateStage.values(), tag.getInt("stage"),
                AggregateStage.DORMANT);
        data.lastStageAdvanceDay = tag.getLong("lastStageAdvanceDay");
        data.ossuaryPos = tag.contains("ossuaryPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("ossuaryPos")) : null;
        data.ossuarySeed = tag.getLong("ossuarySeed");
        data.awakeningEligible = tag.getBoolean("awakeningEligible");
        data.fightStarted = tag.getBoolean("fightStarted");
        data.aggregateResolved = tag.getBoolean("aggregateResolved");
        data.activeAggregateId = tag.hasUUID("activeAggregateId")
                ? tag.getUUID("activeAggregateId") : null;
        data.fightId = tag.hasUUID("fightId") ? tag.getUUID("fightId") : null;
        data.participantCountSnapshot = Math.max(0, tag.getInt("participants"));
        data.overfeedPressure = Math.max(0.0D, tag.getDouble("overfeedPressure"));
        for (Tag value : tag.getList("lockedTraits", Tag.TAG_STRING)) {
            try {
                data.lockedTraits.add(AggregateLineage.valueOf(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag.contains("dominantTrait", Tag.TAG_STRING)) {
            try {
                data.dominantTrait = AggregateLineage.valueOf(tag.getString("dominantTrait"));
            } catch (IllegalArgumentException ignored) {
                data.dominantTrait = null;
            }
        }
        data.fightHealth = Math.max(0.0F, tag.getFloat("fightHealth"));
        data.fightMaxHealth = Math.max(0.0F, tag.getFloat("fightMaxHealth"));
        data.fightPosition = tag.contains("fightPosition", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("fightPosition")) : null;
        data.fightPhase = safeEnum(AggregatePhase.values(), tag.getInt("fightPhase"),
                AggregatePhase.AWAKENING);
        data.coreRewardGranted = tag.getBoolean("coreRewardGranted");
        data.missingEntityChecks = Math.max(0, tag.getInt("missingEntityChecks"));
        readPositions(tag, "ossuaryBlocks", data.ossuaryBlocks);
        readPositions(tag, "temporaryBlocks", data.temporaryBlocks);
        data.stillpointPos = tag.contains("stillpointPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("stillpointPos")) : null;
        if (tag.contains("stillpointDimension", Tag.TAG_STRING)) {
            data.stillpointDimension = ResourceLocation.tryParse(
                    tag.getString("stillpointDimension"));
        }
        data.dischargeMask = Math.max(0, tag.getInt("dischargeMask"));
        for (Tag value : tag.getList("reinforcements", Tag.TAG_COMPOUND)) {
            if (value instanceof CompoundTag recordTag) {
                ReinforcementRecord record = ReinforcementRecord.load(recordTag);
                if (record != null) data.reinforcements.add(record);
            }
        }
        if (data.fightStarted && data.fightId == null) {
            data.fightId = data.activeAggregateId != null
                    ? data.activeAggregateId
                    : UUID.nameUUIDFromBytes(("aggregate:" + data.ossuarySeed).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        tag.putDouble("pressure", convergencePressure);
        for (AggregateLineage lineage : AggregateLineage.values()) {
            tag.putDouble("lineage_" + lineage.name().toLowerCase(),
                    lineagePressure.getOrDefault(lineage, 0.0D));
        }
        tag.putInt("stage", stage.ordinal());
        tag.putLong("lastStageAdvanceDay", lastStageAdvanceDay);
        if (ossuaryPos != null) tag.putLong("ossuaryPos", ossuaryPos.asLong());
        tag.putLong("ossuarySeed", ossuarySeed);
        tag.putBoolean("awakeningEligible", awakeningEligible);
        tag.putBoolean("fightStarted", fightStarted);
        tag.putBoolean("aggregateResolved", aggregateResolved);
        if (activeAggregateId != null) tag.putUUID("activeAggregateId", activeAggregateId);
        if (fightId != null) tag.putUUID("fightId", fightId);
        tag.putInt("participants", participantCountSnapshot);
        tag.putDouble("overfeedPressure", overfeedPressure);
        ListTag traits = new ListTag();
        for (AggregateLineage lineage : lockedTraits) {
            traits.add(net.minecraft.nbt.StringTag.valueOf(lineage.name()));
        }
        tag.put("lockedTraits", traits);
        if (dominantTrait != null) tag.putString("dominantTrait", dominantTrait.name());
        tag.putFloat("fightHealth", fightHealth);
        tag.putFloat("fightMaxHealth", fightMaxHealth);
        if (fightPosition != null) tag.putLong("fightPosition", fightPosition.asLong());
        tag.putInt("fightPhase", fightPhase.ordinal());
        tag.putBoolean("coreRewardGranted", coreRewardGranted);
        tag.putInt("missingEntityChecks", missingEntityChecks);
        writePositions(tag, "ossuaryBlocks", ossuaryBlocks);
        writePositions(tag, "temporaryBlocks", temporaryBlocks);
        if (stillpointPos != null) tag.putLong("stillpointPos", stillpointPos.asLong());
        if (stillpointDimension != null) {
            tag.putString("stillpointDimension", stillpointDimension.toString());
        }
        tag.putInt("dischargeMask", dischargeMask);
        ListTag reinforcementTags = new ListTag();
        for (ReinforcementRecord record : reinforcements) {
            reinforcementTags.add(record.save());
        }
        tag.put("reinforcements", reinforcementTags);
        return tag;
    }

    public boolean addPressure(AggregatePressurePolicy.Contribution contribution) {
        if (aggregateResolved || contribution == null || !contribution.counts()) return false;
        convergencePressure += contribution.pressure();
        lineagePressure.merge(contribution.lineage(), contribution.pressure(), Double::sum);
        if (convergencePressure > FrozenDawnConfig.AGGREGATE_AWAKENING_PRESSURE.get()) {
            overfeedPressure = convergencePressure
                    - FrozenDawnConfig.AGGREGATE_AWAKENING_PRESSURE.get();
        }
        setDirty();
        return true;
    }

    public double pressure() {
        return convergencePressure;
    }

    public EnumMap<AggregateLineage, Double> lineagePressure() {
        return new EnumMap<>(lineagePressure);
    }

    public AggregateStage stage() {
        return stage;
    }

    public long lastStageAdvanceDay() {
        return lastStageAdvanceDay;
    }

    public void advanceStage(AggregateStage next, long day) {
        if (aggregateResolved || next.ordinal() <= stage.ordinal()) return;
        stage = next;
        lastStageAdvanceDay = day;
        if (next == AggregateStage.AWAKENING_ELIGIBLE) awakeningEligible = true;
        setDirty();
    }

    public Optional<BlockPos> ossuaryPos() {
        return Optional.ofNullable(ossuaryPos);
    }

    public long ossuarySeed() {
        return ossuarySeed;
    }

    public void setOssuary(BlockPos pos, long seed) {
        if (ossuaryPos != null || pos == null) return;
        ossuaryPos = pos.immutable();
        ossuarySeed = seed;
        setDirty();
    }

    /** Explicit test-only relocation used by the debug spawn command. */
    public void debugRelocateOssuary(BlockPos pos, long seed) {
        if (pos == null) return;
        ossuaryPos = pos.immutable();
        ossuarySeed = seed;
        ossuaryBlocks.clear();
        temporaryBlocks.clear();
        setDirty();
    }

    public boolean awakeningEligible() {
        return awakeningEligible && !fightStarted && !aggregateResolved;
    }

    public boolean fightStarted() {
        return fightStarted;
    }

    public boolean resolved() {
        return aggregateResolved;
    }

    public Optional<UUID> activeAggregateId() {
        return Optional.ofNullable(activeAggregateId);
    }

    public Optional<UUID> fightId() {
        return Optional.ofNullable(fightId);
    }

    public void beginFight(UUID id, int participants, float maxHealth, BlockPos position) {
        fightStarted = true;
        awakeningEligible = false;
        stage = AggregateStage.ACTIVE;
        activeAggregateId = id;
        fightId = UUID.randomUUID();
        dischargeMask = 0;
        reinforcements.clear();
        missingEntityChecks = 0;
        participantCountSnapshot = Math.max(1, participants);
        fightMaxHealth = Math.max(1.0F, maxHealth);
        fightHealth = fightMaxHealth;
        fightPosition = position == null ? ossuaryPos : position.immutable();
        fightPhase = AggregatePhase.AWAKENING;
        lockedTraits.clear();
        lockedTraits.addAll(AggregatePressurePolicy.lockTraits(lineagePressure, ossuarySeed));
        dominantTrait = AggregatePressurePolicy.dominant(lineagePressure);
        setDirty();
    }

    public boolean dischargeSpent(int wave) {
        return wave >= 0 && wave < Integer.SIZE && (dischargeMask & 1 << wave) != 0;
    }

    public int dischargeScars() {
        return Integer.bitCount(dischargeMask & 0b11);
    }

    public boolean reserveDischarge(int wave, List<AggregateLineage> lineages,
                                    List<BlockPos> positions, long gameTime) {
        if (fightId == null || dischargeSpent(wave) || lineages == null
                || positions == null || lineages.isEmpty()
                || lineages.size() != positions.size()) return false;
        dischargeMask |= 1 << wave;
        for (int i = 0; i < lineages.size(); i++) {
            UUID recordId = UUID.nameUUIDFromBytes((fightId + ":" + wave + ":" + i)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            reinforcements.add(new ReinforcementRecord(recordId, fightId, wave,
                    lineages.get(i), positions.get(i).immutable(),
                    AggregateReinforcementState.PENDING, null,
                    gameTime + AggregateDischargePolicy.EJECTION_TICK
                            + AggregateDischargePolicy.CHILD_LIFETIME_TICKS));
        }
        setDirty();
        return true;
    }

    public void markDischargeSpent(int wave) {
        if (!dischargeSpent(wave)) {
            dischargeMask |= 1 << wave;
            setDirty();
        }
    }

    public List<ReinforcementSnapshot> reinforcements() {
        return reinforcements.stream().map(ReinforcementRecord::snapshot).toList();
    }

    public List<ReinforcementSnapshot> pendingReinforcements(int wave) {
        return reinforcements.stream()
                .filter(record -> record.wave == wave
                        && record.state == AggregateReinforcementState.PENDING)
                .map(ReinforcementRecord::snapshot).toList();
    }

    public int activeSubstantialReinforcements() {
        return (int) reinforcements.stream().filter(record ->
                record.state == AggregateReinforcementState.ACTIVE
                        && record.lineage != AggregateLineage.FROSTWRITHE).count();
    }

    public void activateReinforcement(UUID recordId, UUID entityId, BlockPos position) {
        findReinforcement(recordId).ifPresent(record -> {
            record.entityId = entityId;
            record.lastPosition = position.immutable();
            record.state = AggregateReinforcementState.ACTIVE;
            setDirty();
        });
    }

    public void updateReinforcement(UUID recordId, BlockPos position) {
        findReinforcement(recordId).ifPresent(record -> {
            BlockPos next = position.immutable();
            if (!Objects.equals(record.lastPosition, next)) {
                record.lastPosition = next;
                setDirty();
            }
        });
    }

    public void markReinforcementDead(UUID entityId) {
        reinforcements.stream().filter(record -> Objects.equals(record.entityId, entityId))
                .findFirst().ifPresent(record -> {
                    record.state = AggregateReinforcementState.DEAD;
                    setDirty();
                });
    }

    public void cancelPendingReinforcements(int wave) {
        boolean changed = false;
        for (ReinforcementRecord record : reinforcements) {
            if (record.wave == wave && record.state == AggregateReinforcementState.PENDING) {
                record.state = AggregateReinforcementState.CANCELLED;
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    public void retireReinforcement(UUID recordId) {
        findReinforcement(recordId).ifPresent(record -> {
            record.state = AggregateReinforcementState.RETIRED;
            setDirty();
        });
    }

    public void retireAllReinforcements() {
        boolean changed = false;
        for (ReinforcementRecord record : reinforcements) {
            if (record.state == AggregateReinforcementState.PENDING
                    || record.state == AggregateReinforcementState.ACTIVE) {
                record.state = AggregateReinforcementState.RETIRED;
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    private Optional<ReinforcementRecord> findReinforcement(UUID recordId) {
        return reinforcements.stream().filter(record -> record.id.equals(recordId)).findFirst();
    }

    public List<AggregateLineage> lockedTraits() {
        return List.copyOf(lockedTraits);
    }

    public Optional<AggregateLineage> dominantTrait() {
        return Optional.ofNullable(dominantTrait);
    }

    public int participantCountSnapshot() {
        return participantCountSnapshot;
    }

    public double overfeedPressure() {
        return overfeedPressure;
    }

    public float fightHealth() {
        return fightHealth;
    }

    public float fightMaxHealth() {
        return fightMaxHealth;
    }

    public AggregatePhase fightPhase() {
        return fightPhase;
    }

    public void snapshotFight(UUID id, BlockPos position, float health,
                              float maxHealth, AggregatePhase phase) {
        BlockPos nextPosition = position == null ? fightPosition : position.immutable();
        float nextHealth = Math.max(0.0F, health);
        float nextMaxHealth = Math.max(1.0F, maxHealth);
        if (Objects.equals(activeAggregateId, id)
                && Objects.equals(fightPosition, nextPosition)
                && Float.compare(fightHealth, nextHealth) == 0
                && Float.compare(fightMaxHealth, nextMaxHealth) == 0
                && fightPhase == phase) return;
        activeAggregateId = id;
        fightPosition = nextPosition;
        fightHealth = nextHealth;
        fightMaxHealth = nextMaxHealth;
        fightPhase = phase;
        setDirty();
    }

    public Optional<BlockPos> fightPosition() {
        return Optional.ofNullable(fightPosition);
    }

    public void resolve() {
        aggregateResolved = true;
        fightStarted = false;
        awakeningEligible = false;
        stage = AggregateStage.RESOLVED;
        activeAggregateId = null;
        fightPosition = null;
        fightHealth = 0.0F;
        fightPhase = AggregatePhase.DEAD;
        retireAllReinforcements();
        setDirty();
    }

    public boolean claimCoreReward() {
        if (coreRewardGranted) return false;
        coreRewardGranted = true;
        setDirty();
        return true;
    }

    public int noteMissingEntity() {
        missingEntityChecks++;
        setDirty();
        return missingEntityChecks;
    }

    public void clearMissingEntityChecks() {
        if (missingEntityChecks != 0) {
            missingEntityChecks = 0;
            setDirty();
        }
    }

    public Set<Long> ossuaryBlocks() {
        return Set.copyOf(ossuaryBlocks);
    }

    public boolean ownsOssuaryBlock(BlockPos pos) {
        return ossuaryBlocks.contains(pos.asLong());
    }

    public void addOssuaryBlock(BlockPos pos) {
        if (ossuaryBlocks.add(pos.asLong())) setDirty();
    }

    public Set<Long> temporaryBlocks() {
        return Set.copyOf(temporaryBlocks);
    }

    public void addTemporaryBlock(BlockPos pos) {
        if (temporaryBlocks.add(pos.asLong())) setDirty();
    }

    public void clearTemporaryBlocks() {
        if (!temporaryBlocks.isEmpty()) {
            temporaryBlocks.clear();
            setDirty();
        }
    }

    public Optional<BlockPos> stillpointPos() {
        return Optional.ofNullable(stillpointPos);
    }

    public Optional<ResourceLocation> stillpointDimension() {
        return Optional.ofNullable(stillpointDimension);
    }

    public void setStillpoint(Level level, BlockPos pos) {
        stillpointPos = pos.immutable();
        stillpointDimension = level.dimension().location();
        setDirty();
    }

    public void clearStillpoint(Level level, BlockPos pos) {
        if (stillpointPos != null && stillpointPos.equals(pos)
                && stillpointDimension != null
                && stillpointDimension.equals(level.dimension().location())) {
            stillpointPos = null;
            stillpointDimension = null;
            setDirty();
        }
    }

    public void debugSetPressure(double pressure) {
        convergencePressure = Math.max(0.0D, pressure);
        overfeedPressure = Math.max(0.0D,
                convergencePressure - FrozenDawnConfig.AGGREGATE_AWAKENING_PRESSURE.get());
        setDirty();
    }

    public void debugSetStage(AggregateStage value, long day) {
        stage = value;
        lastStageAdvanceDay = day - 1L;
        awakeningEligible = value == AggregateStage.AWAKENING_ELIGIBLE;
        setDirty();
    }

    public void debugSetLineage(AggregateLineage lineage, double value) {
        lineagePressure.put(lineage, Math.max(0.0D, value));
        setDirty();
    }

    public void debugResetDischarges() {
        dischargeMask = 0;
        reinforcements.clear();
        setDirty();
    }

    /** Rearms only the once-per-world encounter authority for explicit debug spawning. */
    public void debugRearmFight() {
        awakeningEligible = false;
        fightStarted = false;
        aggregateResolved = false;
        activeAggregateId = null;
        fightId = null;
        dischargeMask = 0;
        reinforcements.clear();
        participantCountSnapshot = 0;
        lockedTraits.clear();
        dominantTrait = null;
        fightHealth = 0.0F;
        fightMaxHealth = 0.0F;
        fightPosition = null;
        fightPhase = AggregatePhase.AWAKENING;
        coreRewardGranted = false;
        missingEntityChecks = 0;
        temporaryBlocks.clear();
        setDirty();
    }

    public void debugReset() {
        convergencePressure = 0.0D;
        lineagePressure.replaceAll((key, value) -> 0.0D);
        stage = AggregateStage.DORMANT;
        lastStageAdvanceDay = -1L;
        ossuaryPos = null;
        ossuarySeed = 0L;
        awakeningEligible = false;
        fightStarted = false;
        aggregateResolved = false;
        activeAggregateId = null;
        fightId = null;
        dischargeMask = 0;
        reinforcements.clear();
        participantCountSnapshot = 0;
        overfeedPressure = 0.0D;
        lockedTraits.clear();
        dominantTrait = null;
        fightHealth = 0.0F;
        fightMaxHealth = 0.0F;
        fightPosition = null;
        fightPhase = AggregatePhase.AWAKENING;
        coreRewardGranted = false;
        missingEntityChecks = 0;
        ossuaryBlocks.clear();
        temporaryBlocks.clear();
        stillpointPos = null;
        stillpointDimension = null;
        setDirty();
    }

    private static <T> T safeEnum(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static void readPositions(CompoundTag tag, String key, Set<Long> output) {
        long[] values = tag.getLongArray(key);
        for (long value : values) output.add(value);
    }

    private static void writePositions(CompoundTag tag, String key, Set<Long> values) {
        tag.putLongArray(key, values.stream().mapToLong(Long::longValue).toArray());
    }

    public record ReinforcementSnapshot(
            UUID id, UUID fightId, int wave, AggregateLineage lineage,
            BlockPos lastPosition, AggregateReinforcementState state,
            UUID entityId, long expiresAt) {
    }

    private static final class ReinforcementRecord {
        private final UUID id;
        private final UUID fightId;
        private final int wave;
        private final AggregateLineage lineage;
        private BlockPos lastPosition;
        private AggregateReinforcementState state;
        private UUID entityId;
        private final long expiresAt;

        private ReinforcementRecord(UUID id, UUID fightId, int wave,
                                    AggregateLineage lineage, BlockPos lastPosition,
                                    AggregateReinforcementState state, UUID entityId,
                                    long expiresAt) {
            this.id = id;
            this.fightId = fightId;
            this.wave = wave;
            this.lineage = lineage;
            this.lastPosition = lastPosition;
            this.state = state;
            this.entityId = entityId;
            this.expiresAt = expiresAt;
        }

        private ReinforcementSnapshot snapshot() {
            return new ReinforcementSnapshot(id, fightId, wave, lineage,
                    lastPosition, state, entityId, expiresAt);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("fightId", fightId);
            tag.putInt("wave", wave);
            tag.putString("lineage", lineage.name());
            tag.putLong("position", lastPosition.asLong());
            tag.putString("state", state.name());
            if (entityId != null) tag.putUUID("entityId", entityId);
            tag.putLong("expiresAt", expiresAt);
            return tag;
        }

        private static ReinforcementRecord load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("fightId")
                    || !tag.contains("lineage", Tag.TAG_STRING)) return null;
            try {
                return new ReinforcementRecord(tag.getUUID("id"), tag.getUUID("fightId"),
                        Math.max(0, tag.getInt("wave")),
                        AggregateLineage.valueOf(tag.getString("lineage")),
                        BlockPos.of(tag.getLong("position")),
                        AggregateReinforcementState.valueOf(tag.getString("state")),
                        tag.hasUUID("entityId") ? tag.getUUID("entityId") : null,
                        tag.getLong("expiresAt"));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
