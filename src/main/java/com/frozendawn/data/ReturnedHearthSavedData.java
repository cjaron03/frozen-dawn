package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthSelectionPolicy;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent world-level identity and maturation records for Returned Hearth sites.
 *
 * Physical reconciliation progress is stored here so bounded placement can resume
 * after chunk unloads or server restarts without duplicating scene pieces.
 */
public final class ReturnedHearthSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 2;

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_returned_hearths";

    private int dataVersion = CURRENT_DATA_VERSION;
    private BlockPos transponderAnchor;
    private boolean selectionComplete;
    private long selectionGameTime = -1L;
    private HearthDisposition globalDisposition = HearthDisposition.DORMANT;
    private boolean permanentOrsathae;
    private final List<HearthRecord> hearths = new ArrayList<>();

    public ReturnedHearthSavedData() {
    }

    public static ReturnedHearthSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReturnedHearthSavedData::new, ReturnedHearthSavedData::load,
                        DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static ReturnedHearthSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        int storedVersion = tag.contains("dataVersion", Tag.TAG_INT) ? tag.getInt("dataVersion") : 0;

        if (tag.contains("transponderAnchor", Tag.TAG_LONG)) {
            state.transponderAnchor = BlockPos.of(tag.getLong("transponderAnchor"));
        }
        state.selectionComplete = tag.getBoolean("selectionComplete");
        state.selectionGameTime = tag.contains("selectionGameTime", Tag.TAG_LONG)
                ? tag.getLong("selectionGameTime")
                : -1L;
        state.globalDisposition = readEnum(tag.getString("globalDisposition"),
                HearthDisposition.class, HearthDisposition.DORMANT);
        state.permanentOrsathae = tag.getBoolean("permanentOrsathae");

        EnumSet<HearthSelectionPolicy.HearthType> loadedTypes =
                EnumSet.noneOf(HearthSelectionPolicy.HearthType.class);
        ListTag hearthList = tag.getList("hearths", Tag.TAG_COMPOUND);
        for (Tag entry : hearthList) {
            if (!(entry instanceof CompoundTag compound)) {
                continue;
            }
            HearthRecord record = HearthRecord.load(compound);
            if (record != null && loadedTypes.add(record.type())) {
                state.hearths.add(record);
            }
        }

        state.selectionComplete = state.selectionComplete || !state.hearths.isEmpty();
        state.dataVersion = CURRENT_DATA_VERSION;
        if (storedVersion != CURRENT_DATA_VERSION) {
            if (storedVersion > CURRENT_DATA_VERSION) {
                FrozenDawn.LOGGER.warn("Returned Hearth data version {} is newer than supported version {}; loading known fields only",
                        storedVersion, CURRENT_DATA_VERSION);
            }
            state.setDirty();
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_DATA_VERSION);
        if (transponderAnchor != null) {
            tag.putLong("transponderAnchor", transponderAnchor.asLong());
        }
        tag.putBoolean("selectionComplete", selectionComplete);
        tag.putLong("selectionGameTime", selectionGameTime);
        tag.putString("globalDisposition", globalDisposition.name());
        tag.putBoolean("permanentOrsathae", permanentOrsathae);

        ListTag hearthList = new ListTag();
        for (HearthRecord hearth : hearths) {
            hearthList.add(hearth.save());
        }
        tag.put("hearths", hearthList);
        return tag;
    }

    public boolean rememberTransponderAnchor(BlockPos pos) {
        if (transponderAnchor != null) {
            return false;
        }
        transponderAnchor = pos.immutable();
        setDirty();
        return true;
    }

    public boolean applySelectionPlan(HearthSelectionPolicy.SelectionPlan plan, long gameTime) {
        if (selectionComplete) {
            return false;
        }

        hearths.clear();
        hearths.add(HearthRecord.planned(plan.major(), gameTime));
        plan.minor().ifPresent(candidate -> hearths.add(HearthRecord.planned(candidate, gameTime)));
        selectionComplete = true;
        selectionGameTime = gameTime;
        setDirty();
        return true;
    }

    public MaturationResult updateMaturation(long currentGameTime, boolean maturationActive) {
        if (!selectionComplete || hearths.isEmpty()) {
            return MaturationResult.noChange();
        }

        long now = Math.max(0L, currentGameTime);
        boolean changed = false;
        int recordsAdvanced = 0;
        long totalTicksAdvanced = 0L;
        List<StageTransition> transitions = new ArrayList<>();

        for (HearthRecord hearth : hearths) {
            long previousUpdate = hearth.lastUpdatedGameTime;
            if (previousUpdate < 0L || now < previousUpdate) {
                hearth.lastUpdatedGameTime = now;
                changed = true;
            } else if (now > previousUpdate) {
                long elapsed = now - previousUpdate;
                hearth.lastUpdatedGameTime = now;
                changed = true;
                if (maturationActive) {
                    long applied = hearth.addMaturity(elapsed);
                    if (applied > 0L) {
                        recordsAdvanced++;
                        totalTicksAdvanced = saturatingAdd(totalTicksAdvanced, applied);
                    }
                }
            }

            changed |= refreshStage(hearth, transitions);
        }

        if (changed) {
            setDirty();
        }
        return new MaturationResult(changed, recordsAdvanced, totalTicksAdvanced, transitions);
    }

    public MaturationResult advanceMaturationForDebug(long ticks, long currentGameTime) {
        if (!selectionComplete || hearths.isEmpty() || ticks <= 0L) {
            return MaturationResult.noChange();
        }

        long now = Math.max(0L, currentGameTime);
        boolean changed = false;
        int recordsAdvanced = 0;
        long totalTicksAdvanced = 0L;
        List<StageTransition> transitions = new ArrayList<>();

        for (HearthRecord hearth : hearths) {
            if (hearth.lastUpdatedGameTime != now) {
                hearth.lastUpdatedGameTime = now;
                changed = true;
            }
            long applied = hearth.addMaturity(ticks);
            if (applied > 0L) {
                recordsAdvanced++;
                totalTicksAdvanced = saturatingAdd(totalTicksAdvanced, applied);
                changed = true;
            }
            changed |= refreshStage(hearth, transitions);
        }

        if (changed) {
            setDirty();
        }
        return new MaturationResult(changed, recordsAdvanced, totalTicksAdvanced, transitions);
    }

    public int dataVersion() {
        return dataVersion;
    }

    public Optional<BlockPos> transponderAnchor() {
        return Optional.ofNullable(transponderAnchor);
    }

    public boolean selectionComplete() {
        return selectionComplete;
    }

    public long selectionGameTime() {
        return selectionGameTime;
    }

    public HearthDisposition globalDisposition() {
        return globalDisposition;
    }

    public boolean permanentOrsathae() {
        return permanentOrsathae;
    }

    public List<HearthRecord> hearths() {
        return List.copyOf(hearths);
    }

    public Optional<HearthRecord> hearth(HearthSelectionPolicy.HearthType type) {
        return hearths.stream().filter(record -> record.type() == type).findFirst();
    }

    public Optional<HearthRecord> hearth(UUID id) {
        return hearths.stream().filter(record -> record.id.equals(id)).findFirst();
    }

    public boolean resolveSurface(UUID id, BlockPos resolvedCenter) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || hearth.surfaceResolved) {
            return false;
        }
        hearth.center = resolvedCenter.immutable();
        hearth.surfaceResolved = true;
        setDirty();
        return true;
    }

    public boolean recordStructureProgress(UUID id, int planVersion, int cursor,
                                           HearthStage appliedStage, boolean complete) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || planVersion < 0 || cursor < 0) {
            return false;
        }

        boolean changed = false;
        if (planVersion > hearth.structurePlanVersion) {
            hearth.structurePlanVersion = planVersion;
            hearth.structureCursor = cursor;
            hearth.structurePlaced = false;
            changed = true;
        } else if (planVersion == hearth.structurePlanVersion && cursor > hearth.structureCursor) {
            hearth.structureCursor = cursor;
            changed = true;
        }

        if (appliedStage.ordinal() > hearth.structureStageApplied.ordinal()) {
            hearth.structureStageApplied = appliedStage;
            changed = true;
        }
        if (complete && !hearth.structurePlaced) {
            hearth.structurePlaced = true;
            changed = true;
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private static boolean refreshStage(HearthRecord hearth, List<StageTransition> transitions) {
        HearthStage desired = HearthMaturationPolicy.stageFor(hearth.type, hearth.maturityTicks);
        if (desired == hearth.stage) {
            return false;
        }

        transitions.add(new StageTransition(hearth.id, hearth.type, hearth.stage, desired));
        hearth.stage = desired;
        return true;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static <E extends Enum<E>> E readEnum(String value, Class<E> enumType, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public enum HearthDisposition {
        DORMANT,
        WATCHFUL,
        AGITATED,
        HOSTILE
    }

    public enum HearthStage {
        PLANNED,
        TRACE,
        FORMED,
        INTACT
    }

    public enum ViolationState {
        NONE,
        SUSPICIOUS,
        VIOLATED
    }

    public record StageTransition(UUID hearthId, HearthSelectionPolicy.HearthType type,
                                  HearthStage previousStage, HearthStage currentStage) {
    }

    public record MaturationResult(boolean changed, int recordsAdvanced,
                                   long totalTicksAdvanced, List<StageTransition> transitions) {
        public MaturationResult {
            transitions = List.copyOf(transitions);
        }

        private static MaturationResult noChange() {
            return new MaturationResult(false, 0, 0L, List.of());
        }
    }

    public static final class HearthRecord {
        private final UUID id;
        private final HearthSelectionPolicy.HearthType type;
        private BlockPos center;
        private final long layoutSeed;
        private boolean surfaceResolved;
        private boolean discovered;
        private long maturityTicks;
        private long lastUpdatedGameTime;
        private HearthStage stage;
        private HearthDisposition mood;
        private ViolationState violationState;
        private boolean structurePlaced;
        private HearthStage structureStageApplied;
        private int structurePlanVersion;
        private int structureCursor;
        private float signalStrength;
        private String boundVariantProfile;
        private long lastPlayerContactGameTime;
        private boolean firstAssessmentFired;
        private boolean firstTransmissionFired;
        private boolean lootTaken;

        private HearthRecord(UUID id, HearthSelectionPolicy.HearthType type, BlockPos center,
                            long layoutSeed) {
            this.id = id;
            this.type = type;
            this.center = center.immutable();
            this.layoutSeed = layoutSeed;
            this.stage = HearthStage.PLANNED;
            this.mood = HearthDisposition.DORMANT;
            this.violationState = ViolationState.NONE;
            this.structureStageApplied = HearthStage.PLANNED;
            this.boundVariantProfile = "";
            this.lastPlayerContactGameTime = -1L;
        }

        private static HearthRecord planned(HearthSelectionPolicy.SiteCandidate candidate, long gameTime) {
            HearthRecord record = new HearthRecord(candidate.id(), candidate.type(),
                    candidate.center(), candidate.layoutSeed());
            record.lastUpdatedGameTime = gameTime;
            return record;
        }

        private static HearthRecord load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.contains("center", Tag.TAG_LONG)) {
                return null;
            }

            HearthSelectionPolicy.HearthType type = readEnum(tag.getString("type"),
                    HearthSelectionPolicy.HearthType.class, null);
            if (type == null) {
                return null;
            }

            HearthRecord record = new HearthRecord(tag.getUUID("id"), type,
                    BlockPos.of(tag.getLong("center")), tag.getLong("layoutSeed"));
            record.surfaceResolved = tag.getBoolean("surfaceResolved");
            record.discovered = tag.getBoolean("discovered");
            record.maturityTicks = Math.max(0L, tag.getLong("maturityTicks"));
            record.lastUpdatedGameTime = tag.getLong("lastUpdatedGameTime");
            record.stage = readEnum(tag.getString("stage"), HearthStage.class, HearthStage.PLANNED);
            record.mood = readEnum(tag.getString("mood"), HearthDisposition.class, HearthDisposition.DORMANT);
            record.violationState = readEnum(tag.getString("violationState"),
                    ViolationState.class, ViolationState.NONE);
            record.structurePlaced = tag.getBoolean("structurePlaced");
            record.structureStageApplied = readEnum(tag.getString("structureStageApplied"),
                    HearthStage.class, HearthStage.PLANNED);
            record.structurePlanVersion = Math.max(0, tag.getInt("structurePlanVersion"));
            record.structureCursor = Math.max(0, tag.getInt("structureCursor"));
            record.signalStrength = Math.max(0.0F, tag.getFloat("signalStrength"));
            record.boundVariantProfile = tag.getString("boundVariantProfile");
            record.lastPlayerContactGameTime = tag.contains("lastPlayerContactGameTime", Tag.TAG_LONG)
                    ? tag.getLong("lastPlayerContactGameTime")
                    : -1L;
            record.firstAssessmentFired = tag.getBoolean("firstAssessmentFired");
            record.firstTransmissionFired = tag.getBoolean("firstTransmissionFired");
            record.lootTaken = tag.getBoolean("lootTaken");
            return record;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("type", type.name());
            tag.putLong("center", center.asLong());
            tag.putLong("layoutSeed", layoutSeed);
            tag.putBoolean("surfaceResolved", surfaceResolved);
            tag.putBoolean("discovered", discovered);
            tag.putLong("maturityTicks", maturityTicks);
            tag.putLong("lastUpdatedGameTime", lastUpdatedGameTime);
            tag.putString("stage", stage.name());
            tag.putString("mood", mood.name());
            tag.putString("violationState", violationState.name());
            tag.putBoolean("structurePlaced", structurePlaced);
            tag.putString("structureStageApplied", structureStageApplied.name());
            tag.putInt("structurePlanVersion", structurePlanVersion);
            tag.putInt("structureCursor", structureCursor);
            tag.putFloat("signalStrength", signalStrength);
            tag.putString("boundVariantProfile", boundVariantProfile);
            tag.putLong("lastPlayerContactGameTime", lastPlayerContactGameTime);
            tag.putBoolean("firstAssessmentFired", firstAssessmentFired);
            tag.putBoolean("firstTransmissionFired", firstTransmissionFired);
            tag.putBoolean("lootTaken", lootTaken);
            return tag;
        }

        private long addMaturity(long ticks) {
            if (ticks <= 0L || maturityTicks == Long.MAX_VALUE) {
                return 0L;
            }
            long applied = Math.min(ticks, Long.MAX_VALUE - maturityTicks);
            maturityTicks += applied;
            return applied;
        }

        public UUID id() {
            return id;
        }

        public HearthSelectionPolicy.HearthType type() {
            return type;
        }

        public BlockPos center() {
            return center;
        }

        public long layoutSeed() {
            return layoutSeed;
        }

        public boolean surfaceResolved() {
            return surfaceResolved;
        }

        public boolean discovered() {
            return discovered;
        }

        public long maturityTicks() {
            return maturityTicks;
        }

        public long lastUpdatedGameTime() {
            return lastUpdatedGameTime;
        }

        public HearthStage stage() {
            return stage;
        }

        public HearthDisposition mood() {
            return mood;
        }

        public ViolationState violationState() {
            return violationState;
        }

        public boolean structurePlaced() {
            return structurePlaced;
        }

        public HearthStage structureStageApplied() {
            return structureStageApplied;
        }

        public int structurePlanVersion() {
            return structurePlanVersion;
        }

        public int structureCursor() {
            return structureCursor;
        }

        public float signalStrength() {
            return signalStrength;
        }

        public String boundVariantProfile() {
            return boundVariantProfile;
        }

        public long lastPlayerContactGameTime() {
            return lastPlayerContactGameTime;
        }

        public boolean firstAssessmentFired() {
            return firstAssessmentFired;
        }

        public boolean firstTransmissionFired() {
            return firstTransmissionFired;
        }

        public boolean lootTaken() {
            return lootTaken;
        }
    }
}
