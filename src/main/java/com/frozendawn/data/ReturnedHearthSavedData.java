package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthEncounterRole;
import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthPopulationRole;
import com.frozendawn.homo.HearthSelectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Persistent world-level identity and maturation records for Returned Hearth sites.
 *
 * Physical reconciliation progress is stored here so bounded placement can resume
 * after chunk unloads or server restarts without duplicating scene pieces.
 */
public final class ReturnedHearthSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 27;
    public static final long CONTACT_SAVE_INTERVAL_TICKS = 200L;
    public static final long NEW_VISIT_GAP_TICKS = 1_200L;

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_returned_hearths";

    private int dataVersion = CURRENT_DATA_VERSION;
    private BlockPos transponderAnchor;
    private boolean selectionComplete;
    private long selectionGameTime = -1L;
    private boolean maeveErased;
    private long maeveErasedGameTime = -1L;
    private boolean undoneSpawningReleased;
    private long postMaeveMoonriseStartDayTime = -1L;
    private long postMaeveMoonElapsedDayTicks = -1L;
    private long postMaeveMoonLastDayTime = -1L;
    private long postMaeveMoonVisualSeed;
    private boolean postMaeveMoonriseStarted;
    private boolean hearthrotSalvationFired;
    private HiveRelationship legacyRelationship = HiveRelationship.NEUTRAL;
    private final Map<UUID, PlayerHiveMemory> playerMemories = new LinkedHashMap<>();
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
        state.maeveErased = tag.getBoolean("maeveErased");
        state.maeveErasedGameTime = tag.contains("maeveErasedGameTime", Tag.TAG_LONG)
                ? tag.getLong("maeveErasedGameTime")
                : -1L;
        state.undoneSpawningReleased = tag.getBoolean("undoneSpawningReleased");
        state.postMaeveMoonriseStartDayTime = readOptionalTime(
                tag, "postMaeveMoonriseStartDayTime");
        state.postMaeveMoonElapsedDayTicks = readOptionalTime(
                tag, "postMaeveMoonElapsedDayTicks");
        state.postMaeveMoonLastDayTime = readOptionalTime(
                tag, "postMaeveMoonLastDayTime");
        state.postMaeveMoonVisualSeed = tag.contains(
                "postMaeveMoonVisualSeed", Tag.TAG_LONG)
                ? tag.getLong("postMaeveMoonVisualSeed") : 0L;
        state.postMaeveMoonriseStarted = tag.getBoolean(
                "postMaeveMoonriseStarted");
        state.hearthrotSalvationFired = tag.getBoolean(
                "hearthrotSalvationFired");
        state.legacyRelationship = loadLegacyRelationship(tag, storedVersion);

        ListTag playerList = tag.getList("playerMemories", Tag.TAG_COMPOUND);
        for (Tag entry : playerList) {
            if (!(entry instanceof CompoundTag compound)) {
                continue;
            }
            PlayerHiveMemory memory = PlayerHiveMemory.load(compound);
            if (memory != null) {
                state.playerMemories.putIfAbsent(memory.playerId(), memory);
            }
        }

        EnumSet<HearthSelectionPolicy.HearthType> loadedTypes =
                EnumSet.noneOf(HearthSelectionPolicy.HearthType.class);
        ListTag hearthList = tag.getList("hearths", Tag.TAG_COMPOUND);
        for (Tag entry : hearthList) {
            if (!(entry instanceof CompoundTag compound)) {
                continue;
            }
            HearthRecord record = HearthRecord.load(compound);
            if (record != null && loadedTypes.add(record.type())) {
                record.migrateHeartState(storedVersion);
                state.hearths.add(record);
            }
        }

        state.selectionComplete = state.selectionComplete || !state.hearths.isEmpty();
        if (!tag.contains("maeveErased", Tag.TAG_BYTE)) {
            state.maeveErased = state.hearths.stream()
                    .anyMatch(hearth -> hearth.heartMaeveErasureStartGameTime() >= 0L);
            if (state.maeveErased) {
                state.maeveErasedGameTime = state.hearths.stream()
                        .mapToLong(HearthRecord::heartMaeveErasureStartGameTime)
                        .filter(time -> time >= 0L)
                        .min().orElse(0L);
            }
        }
        boolean conductChanged = state.recomputeAllHearthConduct();
        state.dataVersion = CURRENT_DATA_VERSION;
        if (storedVersion != CURRENT_DATA_VERSION || conductChanged) {
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
        tag.putBoolean("maeveErased", maeveErased);
        tag.putLong("maeveErasedGameTime", maeveErasedGameTime);
        tag.putBoolean("undoneSpawningReleased", undoneSpawningReleased);
        tag.putLong("postMaeveMoonriseStartDayTime", postMaeveMoonriseStartDayTime);
        tag.putLong("postMaeveMoonElapsedDayTicks", postMaeveMoonElapsedDayTicks);
        tag.putLong("postMaeveMoonLastDayTime", postMaeveMoonLastDayTime);
        tag.putLong("postMaeveMoonVisualSeed", postMaeveMoonVisualSeed);
        tag.putBoolean("postMaeveMoonriseStarted", postMaeveMoonriseStarted);
        tag.putBoolean("hearthrotSalvationFired", hearthrotSalvationFired);
        tag.putString("legacyRelationship", legacyRelationship.name());

        ListTag playerList = new ListTag();
        for (PlayerHiveMemory memory : playerMemories.values()) {
            playerList.add(memory.save());
        }
        tag.put("playerMemories", playerList);

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

    public boolean maeveErased() {
        return maeveErased;
    }

    public long maeveErasedGameTime() {
        return maeveErasedGameTime;
    }

    public boolean undoneSpawningReleased() {
        return undoneSpawningReleased;
    }

    public long postMaeveMoonriseStartDayTime() {
        return postMaeveMoonriseStartDayTime;
    }

    public long postMaeveMoonElapsedDayTicks() {
        return postMaeveMoonElapsedDayTicks;
    }

    public long postMaeveMoonLastDayTime() {
        return postMaeveMoonLastDayTime;
    }

    public long postMaeveMoonVisualSeed() {
        return postMaeveMoonVisualSeed;
    }

    public boolean postMaeveMoonriseStarted() {
        return postMaeveMoonriseStarted;
    }

    public boolean hearthrotSalvationFired() {
        return hearthrotSalvationFired;
    }

    public boolean markHearthrotSalvationFired() {
        if (hearthrotSalvationFired) {
            return false;
        }
        hearthrotSalvationFired = true;
        setDirty();
        return true;
    }

    public boolean resetHearthrotSalvationForDebug() {
        if (!hearthrotSalvationFired) {
            return false;
        }
        hearthrotSalvationFired = false;
        setDirty();
        return true;
    }

    public boolean schedulePostMaeveMoonrise(
            long startDayTime, long currentDayTime, long visualSeed) {
        if (!maeveErased || postMaeveMoonriseStartDayTime >= 0L) {
            return false;
        }
        postMaeveMoonriseStartDayTime = Math.max(0L, startDayTime);
        postMaeveMoonElapsedDayTicks = 0L;
        postMaeveMoonLastDayTime = Math.max(0L, currentDayTime);
        postMaeveMoonVisualSeed = visualSeed;
        postMaeveMoonriseStarted = currentDayTime >= startDayTime;
        setDirty();
        return true;
    }

    /** Advances only on positive day-time movement; clock rollback never heals the Moon. */
    public long advancePostMaeveMoon(long currentDayTime) {
        if (postMaeveMoonriseStartDayTime < 0L) {
            return 0L;
        }
        long current = Math.max(0L, currentDayTime);
        long previous = postMaeveMoonLastDayTime < 0L
                ? current : postMaeveMoonLastDayTime;
        long delta = 0L;
        if (!postMaeveMoonriseStarted) {
            if (current >= postMaeveMoonriseStartDayTime) {
                postMaeveMoonriseStarted = true;
                delta = Math.max(0L, current
                        - Math.max(previous, postMaeveMoonriseStartDayTime));
            }
        } else {
            delta = com.frozendawn.homo.PostMaeveMoonPolicy
                    .positiveDayTimeAdvance(previous, current);
        }
        postMaeveMoonLastDayTime = current;
        if (delta > 0L) {
            postMaeveMoonElapsedDayTicks = Math.max(0L,
                    postMaeveMoonElapsedDayTicks) + delta;
        }
        if (delta > 0L || current != previous || postMaeveMoonriseStarted) {
            setDirty();
        }
        return delta;
    }

    public boolean startPostMaeveMoonriseForDebug(long dayTime, long visualSeed) {
        if (!maeveErased) {
            return false;
        }
        postMaeveMoonriseStartDayTime = Math.max(0L, dayTime);
        postMaeveMoonElapsedDayTicks = 0L;
        postMaeveMoonLastDayTime = Math.max(0L, dayTime);
        postMaeveMoonVisualSeed = visualSeed;
        postMaeveMoonriseStarted = true;
        setDirty();
        return true;
    }

    public boolean setPostMaeveMoonDamageAgeForDebug(
            long damageAgeTicks, long dayTime, long visualSeed) {
        if (!maeveErased) {
            return false;
        }
        postMaeveMoonriseStartDayTime = Math.max(0L,
                dayTime - com.frozendawn.homo.PostMaeveMoonPolicy.FIRST_RISE_TICKS
                        - Math.max(0L, damageAgeTicks));
        postMaeveMoonElapsedDayTicks = com.frozendawn.homo.PostMaeveMoonPolicy
                .FIRST_RISE_TICKS + Math.max(0L, damageAgeTicks);
        postMaeveMoonLastDayTime = Math.max(0L, dayTime);
        postMaeveMoonVisualSeed = visualSeed;
        postMaeveMoonriseStarted = true;
        setDirty();
        return true;
    }

    public boolean resetPostMaeveMoonForDebug() {
        boolean changed = postMaeveMoonriseStartDayTime >= 0L
                || postMaeveMoonElapsedDayTicks >= 0L
                || postMaeveMoonLastDayTime >= 0L
                || postMaeveMoonVisualSeed != 0L
                || postMaeveMoonriseStarted;
        postMaeveMoonriseStartDayTime = -1L;
        postMaeveMoonElapsedDayTicks = -1L;
        postMaeveMoonLastDayTime = -1L;
        postMaeveMoonVisualSeed = 0L;
        postMaeveMoonriseStarted = false;
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Production transition. Maeve erasure is deliberately one-way. */
    public boolean markMaeveErased(long gameTime) {
        if (maeveErased) {
            return false;
        }
        maeveErased = true;
        maeveErasedGameTime = Math.max(0L, gameTime);
        undoneSpawningReleased = false;
        resetPostMaeveMoonForDebug();
        setDirty();
        return true;
    }

    public boolean markUndoneSpawningReleased() {
        if (!maeveErased || undoneSpawningReleased) {
            return false;
        }
        undoneSpawningReleased = true;
        setDirty();
        return true;
    }

    /** Debug-only escape hatch used by the explicit post-Maeve test command. */
    public boolean setMaeveErasedForDebug(boolean erased, long gameTime) {
        if (maeveErased == erased
                && (erased || maeveErasedGameTime < 0L)) {
            return false;
        }
        maeveErased = erased;
        maeveErasedGameTime = erased ? Math.max(0L, gameTime) : -1L;
        undoneSpawningReleased = erased;
        resetPostMaeveMoonForDebug();
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

    public HiveRelationship legacyRelationship() {
        return legacyRelationship;
    }

    public HiveRelationship relationship(UUID playerId) {
        PlayerHiveMemory memory = playerMemories.get(playerId);
        return memory == null ? legacyRelationship : memory.relationship;
    }

    public Optional<PlayerHiveMemory> playerMemory(UUID playerId) {
        return Optional.ofNullable(playerMemories.get(playerId));
    }

    public List<PlayerHiveMemory> playerMemories() {
        return List.copyOf(playerMemories.values());
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

    public DiscoveryResult recordSurveyObservation(UUID id, float signalStrength,
                                                    boolean catalogue) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null) {
            return DiscoveryResult.missing();
        }

        float normalizedStrength = Float.isFinite(signalStrength)
                ? Math.max(0.0F, Math.min(1.0F, signalStrength))
                : 0.0F;
        boolean changed = false;
        if (normalizedStrength > hearth.signalStrength) {
            hearth.signalStrength = normalizedStrength;
            changed = true;
        }

        boolean newlyDiscovered = catalogue && !hearth.discovered;
        if (newlyDiscovered) {
            hearth.discovered = true;
            changed = true;
        }

        if (changed) {
            setDirty();
        }
        return new DiscoveryResult(changed, newlyDiscovered,
                hearth.discovered, hearth.signalStrength);
    }

    public int resetSurveyDiscoveryForDebug() {
        int changed = 0;
        for (HearthRecord hearth : hearths) {
            if (hearth.discovered || hearth.signalStrength > 0.0F) {
                hearth.discovered = false;
                hearth.signalStrength = 0.0F;
                changed++;
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
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
            hearth.structureDegradedCursors.clear();
            hearth.structureReconcileAttempts = 0;
            hearth.structureDegradedAccepted = false;
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

    /**
     * Remembers that a structural placement at {@code cursor} could not be applied. Recorded
     * rather than merely counted, so a later pass knows where to go back to and
     * {@code /fd hearth status verbose} can still report the damage after a restart.
     */
    public boolean recordDegradedPlacement(UUID id, int planVersion, int cursor) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || cursor < 0 || planVersion != hearth.structurePlanVersion) {
            return false;
        }
        if (hearth.structureDegradedCursors.add(cursor)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Clears a previously degraded cursor once that cell has been placed successfully. */
    public boolean clearDegradedPlacement(UUID id, int planVersion, int cursor) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || planVersion != hearth.structurePlanVersion
                || hearth.structureDegradedCursors.isEmpty()) {
            return false;
        }
        if (hearth.structureDegradedCursors.remove(cursor)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * Rewinds the cursor so the next pass revisits the degraded cells, leaving the scene
     * explicitly unfinished. Without the rewind the stored cursor sits at the end of the layout
     * and every subsequent pass would be a no-op.
     */
    public boolean scheduleStructureReaudit(UUID id, int planVersion, int cursor,
                                            HearthStage appliedStage) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || cursor < 0 || planVersion != hearth.structurePlanVersion) {
            return false;
        }
        hearth.structureCursor = cursor;
        hearth.structurePlaced = false;
        hearth.structureReconcileAttempts++;
        if (appliedStage.ordinal() > hearth.structureStageApplied.ordinal()) {
            hearth.structureStageApplied = appliedStage;
        }
        setDirty();
        return true;
    }

    /**
     * Stops retrying and lets the scene finish with the holes it has. Used when an obstruction
     * has outlasted every re-audit and is therefore something the player put there on purpose.
     */
    public boolean acceptDegradedStructure(UUID id, int planVersion, int cursor,
                                           HearthStage appliedStage) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || cursor < 0 || planVersion != hearth.structurePlanVersion) {
            return false;
        }
        hearth.structureCursor = cursor;
        hearth.structurePlaced = true;
        hearth.structureDegradedAccepted = true;
        if (appliedStage.ordinal() > hearth.structureStageApplied.ordinal()) {
            hearth.structureStageApplied = appliedStage;
        }
        setDirty();
        return true;
    }

    /**
     * Reopens a scene we had given up on, rewinding to the first cell still missing. Without
     * this an accepted hole is permanent even after the player clears what caused it, which
     * makes the give-up decision unrecoverable rather than merely final.
     */
    public boolean reopenDegradedStructure(UUID id) {
        HearthRecord hearth = hearth(id).orElse(null);
        if (hearth == null || !hearth.structureDegradedAccepted) {
            return false;
        }
        hearth.structureDegradedAccepted = false;
        hearth.structureReconcileAttempts = 0;
        hearth.structurePlaced = false;
        hearth.structureCursor = hearth.structureDegradedCursors.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        setDirty();
        return true;
    }

    public boolean bindWatcher(UUID hearthId, UUID entityId, String profile) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.watcherSpawned) {
            return false;
        }
        hearth.watcherSpawned = true;
        hearth.watcherEntityId = entityId;
        hearth.boundVariantProfile = profile == null ? "" : profile;
        setDirty();
        return true;
    }

    public boolean clearWatcherBindingForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (!hearth.watcherSpawned && hearth.watcherEntityId == null
                && hearth.boundVariantProfile.isBlank())) {
            return false;
        }
        hearth.watcherSpawned = false;
        hearth.watcherEntityId = null;
        hearth.boundVariantProfile = "";
        setDirty();
        return true;
    }

    public boolean bindArchitectAssessor(UUID hearthId, UUID entityId, String profile) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.architectAssessorSpawned) {
            return false;
        }
        hearth.architectAssessorSpawned = true;
        hearth.architectAssessorEntityId = entityId;
        hearth.architectAssessorProfile = profile == null ? "" : profile;
        setDirty();
        return true;
    }

    public boolean clearArchitectAssessorBindingForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (!hearth.architectAssessorSpawned
                && hearth.architectAssessorEntityId == null
                && hearth.architectAssessorProfile.isBlank())) {
            return false;
        }
        hearth.architectAssessorSpawned = false;
        hearth.architectAssessorEntityId = null;
        hearth.architectAssessorProfile = "";
        setDirty();
        return true;
    }

    public boolean bindPopulationResident(UUID hearthId, HearthPopulationRole role, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || role == null || entityId == null) {
            return false;
        }
        HearthResidentBinding binding = hearth.populationResidents.computeIfAbsent(
                role, HearthResidentBinding::new);
        if (binding.entityId != null || binding.permanentlyVacant) {
            return false;
        }
        binding.entityId = entityId;
        binding.respawnAfterGameTime = -1L;
        setDirty();
        return true;
    }

    public boolean markPopulationResidentMissing(UUID hearthId, HearthPopulationRole role,
                                                    UUID entityId, long gameTime) {
        return markPopulationResidentMissing(
                hearthId, role, entityId, gameTime, false);
    }

    public boolean markPopulationResidentMissing(UUID hearthId, HearthPopulationRole role,
                                                   UUID entityId, long gameTime,
                                                   boolean permanentlyVacant) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || role == null || entityId == null) {
            return false;
        }
        HearthResidentBinding binding = hearth.populationResidents.get(role);
        if (binding == null || !entityId.equals(binding.entityId)) {
            return false;
        }
        binding.entityId = null;
        binding.permanentlyVacant = permanentlyVacant;
        binding.respawnAfterGameTime = permanentlyVacant
                ? -1L
                : Math.max(0L, gameTime) + HearthPopulationPolicy.RESPAWN_DELAY_TICKS;
        setDirty();
        return true;
    }

    public boolean initializeCombatRoster(
            UUID hearthId, Map<UUID, HearthEncounterRole> assignments) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.combatRosterInitialized || assignments == null) {
            return false;
        }
        Map<UUID, HearthEncounterRole> preRosterCasualties = new LinkedHashMap<>();
        hearth.combatRoster.forEach((entityId, role) -> {
            if (role == HearthEncounterRole.SPENT) {
                preRosterCasualties.put(entityId, role);
            }
        });
        hearth.combatRoster.clear();
        hearth.combatRoster.putAll(preRosterCasualties);
        assignments.forEach((entityId, role) -> {
            if (entityId != null && role != null && role != HearthEncounterRole.UNASSIGNED) {
                hearth.combatRoster.putIfAbsent(entityId, role);
            }
        });
        hearth.combatRosterInitialized = true;
        setDirty();
        return true;
    }

    public boolean recordPreRosterCasualty(UUID hearthId, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null || hearth.combatRosterInitialized
                || hearth.combatRoster.containsKey(entityId)) {
            return false;
        }
        hearth.combatRoster.put(entityId, HearthEncounterRole.SPENT);
        setDirty();
        return true;
    }

    public HearthEncounterRole encounterRole(UUID hearthId, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null) {
            return HearthEncounterRole.UNASSIGNED;
        }
        return hearth.combatRoster.getOrDefault(
                entityId, HearthEncounterRole.UNASSIGNED);
    }

    public boolean setEncounterRole(
            UUID hearthId, UUID entityId, HearthEncounterRole role) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null || role == null
                || role == HearthEncounterRole.UNASSIGNED) {
            return false;
        }
        HearthEncounterRole previous = hearth.combatRoster.put(entityId, role);
        hearth.combatRosterInitialized = true;
        if (previous == role) {
            return false;
        }
        setDirty();
        return true;
    }

    public int releaseEncounterTethers(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.combatRoster.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Map.Entry<UUID, HearthEncounterRole> entry : hearth.combatRoster.entrySet()) {
            if (entry.getValue() == HearthEncounterRole.TETHERED) {
                entry.setValue(HearthEncounterRole.RESERVED);
                changed++;
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    public int pacifyCombatRoster(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.combatRoster.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Map.Entry<UUID, HearthEncounterRole> entry : hearth.combatRoster.entrySet()) {
            if (entry.getValue() == HearthEncounterRole.DISPATCHED
                    || entry.getValue() == HearthEncounterRole.RESERVED
                    || entry.getValue() == HearthEncounterRole.TETHERED) {
                entry.setValue(HearthEncounterRole.BYSTANDER);
                changed++;
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    public boolean recordCongregationCasualty(
            UUID playerId, UUID hearthId, UUID entityId, long gameTime) {
        if (playerId == null || hearthId == null || entityId == null
                || hearth(hearthId).isEmpty()) {
            return false;
        }
        PlayerHiveMemory memory = playerMemories.computeIfAbsent(
                playerId, id -> new PlayerHiveMemory(id, legacyRelationship));
        if (!memory.recordCongregationCasualty(
                entityId, hearthId, Math.max(0L, gameTime))) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean isRecordedCongregationCasualty(UUID entityId) {
        return entityId != null && playerMemories.values().stream()
                .anyMatch(memory -> memory.congregationCasualtyIds.contains(entityId));
    }

    public int clearPopulationBindingsForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.populationResidents.isEmpty()) {
            return 0;
        }
        int cleared = hearth.populationResidents.size();
        hearth.populationResidents.clear();
        setDirty();
        return cleared;
    }

    public boolean bindMasterArchitect(UUID hearthId, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null || hearth.masterArchitectDefeated
                || hearth.masterArchitectEntityId != null) {
            return false;
        }
        hearth.masterArchitectEntityId = entityId;
        setDirty();
        return true;
    }

    public boolean markMasterArchitectDefeated(UUID hearthId, UUID entityId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null
                || !entityId.equals(hearth.masterArchitectEntityId)
                || hearth.masterArchitectDefeated) {
            return false;
        }
        hearth.masterArchitectEntityId = null;
        hearth.masterArchitectDefeated = true;
        hearth.masterArchitectDefeatedGameTime = Math.max(0L, gameTime);
        setDirty();
        return true;
    }

    public boolean prepareHeartFormation(
            UUID hearthId,
            BlockPos anchor,
            List<HeartFragmentSnapshot> fragments) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.type != HearthSelectionPolicy.HearthType.MAJOR) {
            return false;
        }
        boolean changed = false;
        if (hearth.heartAnchor == null && anchor != null) {
            hearth.heartAnchor = anchor.immutable();
            hearth.heartLayoutSeed = hearth.layoutSeed ^ anchor.asLong()
                    ^ 0x48454152544C4154L;
            changed = true;
        }
        if (hearth.heartFragments.isEmpty() && fragments != null && !fragments.isEmpty()) {
            hearth.heartFragments.addAll(fragments.stream().limit(40).toList());
            changed = true;
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean beginMasterArchitectStormAftermath(
            UUID hearthId, long gameTime, float fieldStrength, UUID killerId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.masterArchitectDefeated
                || hearth.hearthStormDead || hearth.masterStormAftermathActive) {
            return false;
        }
        hearth.masterStormAftermathActive = true;
        hearth.masterStormAftermathStartGameTime = Math.max(0L, gameTime);
        hearth.masterStormAftermathStrength = Math.max(0.0F, Math.min(1.0F, fieldStrength));
        hearth.heartFieldStrength = hearth.masterStormAftermathStrength;
        hearth.masterStormAftermathKillerId = killerId;
        hearth.watchedStopWatchingGranted = false;
        setDirty();
        return true;
    }

    public boolean startHeartFormation(UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.type != HearthSelectionPolicy.HearthType.MAJOR
                || hearth.heartLive || hearth.heartFormationStartGameTime >= 0L) {
            return false;
        }
        if (hearth.heartAnchor == null) {
            hearth.heartAnchor = hearth.center.immutable();
        }
        if (hearth.heartLayoutSeed == 0L) {
            hearth.heartLayoutSeed = hearth.layoutSeed ^ hearth.heartAnchor.asLong()
                    ^ 0x48454152544C4154L;
        }
        hearth.heartFormationStartGameTime = Math.max(0L, gameTime);
        hearth.heartFormationSuppressed = false;
        hearth.heartAdvancementFired = false;
        hearth.heartLive = false;
        hearth.heartConvergenceStarted = false;
        hearth.heartMusicActive = true;
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartSwarmAnnounced = false;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        setDirty();
        return true;
    }

    public boolean markHeartAdvancementFired(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartAdvancementFired) {
            return false;
        }
        hearth.heartAdvancementFired = true;
        setDirty();
        return true;
    }

    public boolean markHeartLive(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartLive) {
            return false;
        }
        hearth.heartLive = true;
        hearth.heartMusicActive = true;
        setDirty();
        return true;
    }

    /** Releases the Heart's exclusive music channel before final erasure. */
    public boolean stopHeartMusic(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartMusicActive) {
            return false;
        }
        hearth.heartMusicActive = false;
        setDirty();
        return true;
    }

    public HeartNodeDamageResult damageHeartMemoryNode(UUID hearthId, int nodeIndex) {
        return damageHeartMemoryNode(hearthId, nodeIndex, -1L);
    }

    public HeartNodeDamageResult damageHeartMemoryNode(
            UUID hearthId, int nodeIndex, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartLive
                || nodeIndex != com.frozendawn.homo.HeartLattice.nextNode(
                hearth.heartDestroyedNodeMask)) {
            return HeartNodeDamageResult.rejected();
        }
        hearth.heartActiveNodeDamage++;
        boolean destroyed = hearth.heartActiveNodeDamage
                >= com.frozendawn.homo.HeartLattice.HITS_PER_NODE;
        if (destroyed) {
            hearth.heartDestroyedNodeMask |= 1 << nodeIndex;
            hearth.heartActiveNodeDamage = 0;
            if (hearth.heartNodeDestroyedGameTimes[nodeIndex] < 0L) {
                hearth.heartNodeDestroyedGameTimes[nodeIndex] = Math.max(0L, gameTime);
            }
        }
        setDirty();
        return new HeartNodeDamageResult(
                true,
                destroyed,
                hearth.heartActiveNodeDamage,
                hearth.heartDestroyedNodeMask);
    }

    public boolean markHeartNodeDebrisLanded(UUID hearthId, int nodeIndex) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || nodeIndex < 0
                || nodeIndex >= com.frozendawn.homo.HeartLattice.NODE_COUNT
                || (hearth.heartDestroyedNodeMask & 1 << nodeIndex) == 0
                || (hearth.heartDebrisLandedMask & 1 << nodeIndex) != 0) {
            return false;
        }
        hearth.heartDebrisLandedMask |= 1 << nodeIndex;
        setDirty();
        return true;
    }

    public boolean markHeartCollapseDebrisLanded(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartCollapseComplete
                || hearth.heartCollapseDebrisLanded) {
            return false;
        }
        hearth.heartCollapseDebrisLanded = true;
        setDirty();
        return true;
    }

    public boolean startHeartCollapse(UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartLive
                || hearth.heartCollapseStartGameTime >= 0L
                || com.frozendawn.homo.HeartLattice.nextNode(
                hearth.heartDestroyedNodeMask) >= 0) {
            return false;
        }
        hearth.heartCollapseStartGameTime = Math.max(0L, gameTime);
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        hearth.heartLive = false;
        hearth.heartActiveNodeDamage = 0;
        setDirty();
        return true;
    }

    public boolean completeHeartCollapse(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartCollapseStartGameTime < 0L
                || hearth.heartCollapseComplete) {
            return false;
        }
        hearth.heartCollapseComplete = true;
        hearth.heartMaeveExposed = true;
        hearth.heartLive = false;
        hearth.heartActiveNodeDamage = 0;
        setDirty();
        return true;
    }

    public boolean startHeartMaeveErasure(
            UUID hearthId, long gameTime, UUID eraserId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartCollapseComplete
                || !hearth.heartMaeveExposed
                || hearth.heartMaeveErasureStartGameTime >= 0L
                || hearth.heartMaeveErasureComplete) {
            return false;
        }
        hearth.heartMaeveErasureStartGameTime = Math.max(0L, gameTime);
        hearth.heartMaeveEraserId = eraserId;
        hearth.heartMaeveForgeAnnounced = false;
        hearth.heartLastWitnessDropped = false;
        hearth.heartFinalAdvancementGranted = false;
        setDirty();
        return true;
    }

    public boolean completeHeartMaeveErasure(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartMaeveErasureStartGameTime < 0L
                || hearth.heartMaeveErasureComplete) {
            return false;
        }
        hearth.heartMaeveErasureComplete = true;
        hearth.heartMaeveExposed = false;
        hearth.heartMusicActive = false;
        hearth.heartLive = false;
        hearth.heartEntityId = null;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        setDirty();
        return true;
    }

    public boolean markHeartMaeveForgeAnnounced(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartMaeveForgeAnnounced) {
            return false;
        }
        hearth.heartMaeveForgeAnnounced = true;
        setDirty();
        return true;
    }

    public boolean markHeartMaeveAftermathSound(UUID hearthId, int soundIndex) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || soundIndex < 0 || soundIndex >= 5) {
            return false;
        }
        int bit = 1 << soundIndex;
        if ((hearth.heartMaeveAftermathSoundMask & bit) != 0) {
            return false;
        }
        hearth.heartMaeveAftermathSoundMask |= bit;
        setDirty();
        return true;
    }

    public boolean markHeartMaeveWorldMessageShown(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartMaeveWorldMessageShown) {
            return false;
        }
        hearth.heartMaeveWorldMessageShown = true;
        setDirty();
        return true;
    }

    public boolean markHeartMaeveCollapseResponsePlayed(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartMaeveCollapseResponsePlayed) {
            return false;
        }
        hearth.heartMaeveCollapseResponsePlayed = true;
        setDirty();
        return true;
    }

    public boolean markHeartMaeveBiologicalWarningPlayed(
            UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartMaeveBiologicalWarningPlayed) {
            return false;
        }
        hearth.heartMaeveBiologicalWarningPlayed = true;
        hearth.heartMaeveBiologicalWarningGameTime = Math.max(0L, gameTime);
        setDirty();
        return true;
    }

    public boolean replayHeartMaeveBiologicalWarningForDebug(
            UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null) {
            return false;
        }
        hearth.heartMaeveBiologicalWarningPlayed = true;
        hearth.heartMaeveBiologicalWarningGameTime = Math.max(0L, gameTime);
        setDirty();
        return true;
    }

    public boolean markHeartLastWitnessDropped(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartLastWitnessDropped) {
            return false;
        }
        hearth.heartLastWitnessDropped = true;
        setDirty();
        return true;
    }

    public boolean markHeartFinalAdvancementGranted(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.heartMaeveErasureComplete
                || hearth.heartFinalAdvancementGranted) {
            return false;
        }
        hearth.heartFinalAdvancementGranted = true;
        setDirty();
        return true;
    }

    public boolean resetHeartMaeveErasureForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.heartMaeveErasureStartGameTime < 0L
                && !hearth.heartMaeveErasureComplete
                && hearth.heartMaeveEraserId == null
                && !hearth.heartFinalAdvancementGranted)) {
            return false;
        }
        resetMaeveErasure(hearth);
        hearth.heartMaeveExposed = hearth.heartCollapseComplete;
        hearth.heartMusicActive = hearth.heartFormationStartGameTime >= 0L;
        setDirty();
        return true;
    }

    public boolean resetHeartMemoryNodesForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.heartDestroyedNodeMask == 0
                && hearth.heartActiveNodeDamage == 0
                && hearth.heartCollapseStartGameTime < 0L
                && !hearth.heartCollapseComplete
                && !hearth.heartMaeveExposed
                && !hearth.heartSwarmAnnounced
                && hearth.heartScavengerNextWaveGameTime < 0L
                && hearth.heartSuccessorEntityId == null
                && hearth.heartSuccessorRespawnGameTime < 0L)) {
            return false;
        }
        hearth.heartDestroyedNodeMask = 0;
        hearth.heartActiveNodeDamage = 0;
        java.util.Arrays.fill(hearth.heartNodeDestroyedGameTimes, -1L);
        hearth.heartDebrisLandedMask = 0;
        hearth.heartCollapseDebrisLanded = false;
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartLive = hearth.heartFormationStartGameTime >= 0L;
        hearth.heartMusicActive = hearth.heartFormationStartGameTime >= 0L;
        hearth.heartSwarmAnnounced = false;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        setDirty();
        return true;
    }

    public boolean setHeartCollapseStageForDebug(
            UUID hearthId, long gameTime,
            com.frozendawn.homo.HeartCollapseStage stage) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || stage == com.frozendawn.homo.HeartCollapseStage.NONE
                || hearth.heartFormationStartGameTime < 0L) {
            return false;
        }
        hearth.heartDestroyedNodeMask = (1 << com.frozendawn.homo.HeartLattice.NODE_COUNT) - 1;
        hearth.heartActiveNodeDamage = 0;
        long elapsed = com.frozendawn.homo.HeartCollapsePolicy
                .elapsedAtStageStart(stage);
        hearth.heartCollapseStartGameTime = Math.max(0L, gameTime - elapsed);
        hearth.heartCollapseComplete = stage
                == com.frozendawn.homo.HeartCollapseStage.DORMANT;
        hearth.heartMaeveExposed = hearth.heartCollapseComplete;
        resetMaeveErasure(hearth);
        hearth.heartCollapseDebrisLanded = false;
        hearth.heartLive = false;
        hearth.heartMusicActive = true;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        setDirty();
        return true;
    }

    public boolean resetHeartCollapseForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.heartCollapseStartGameTime < 0L
                && !hearth.heartCollapseComplete && !hearth.heartMaeveExposed)) {
            return false;
        }
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartCollapseDebrisLanded = false;
        hearth.heartLive = hearth.heartFormationStartGameTime >= 0L;
        hearth.heartMusicActive = hearth.heartFormationStartGameTime >= 0L;
        hearth.heartSwarmAnnounced = false;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        setDirty();
        return true;
    }

    /** Node five removes the hive's stored account of this player, not shared world facts. */
    public boolean erasePlayerFromHive(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean changed = playerMemories.remove(playerId) != null;
        for (HearthRecord hearth : hearths) {
            changed |= hearth.playerContacts.remove(playerId) != null;
        }
        changed |= recomputeAllHearthConduct();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private static void resetMaeveErasure(HearthRecord hearth) {
        hearth.heartMaeveErasureStartGameTime = -1L;
        hearth.heartMaeveErasureComplete = false;
        hearth.heartMaeveEraserId = null;
        hearth.heartMaeveForgeAnnounced = false;
        hearth.heartMaeveAftermathSoundMask = 0;
        hearth.heartMaeveWorldMessageShown = false;
        hearth.heartMaeveCollapseResponsePlayed = false;
        hearth.heartMaeveBiologicalWarningPlayed = false;
        hearth.heartMaeveBiologicalWarningGameTime = -1L;
        hearth.heartLastWitnessDropped = false;
        hearth.heartFinalAdvancementGranted = false;
    }

    public boolean markHeartConvergenceStarted(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartConvergenceStarted) {
            return false;
        }
        hearth.heartConvergenceStarted = true;
        setDirty();
        return true;
    }

    public boolean bindHeartEntity(UUID hearthId, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null || entityId.equals(hearth.heartEntityId)) {
            return false;
        }
        hearth.heartEntityId = entityId;
        setDirty();
        return true;
    }

    public boolean clearHeartBinding(UUID hearthId, UUID entityId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartEntityId == null
                || (entityId != null && !entityId.equals(hearth.heartEntityId))) {
            return false;
        }
        hearth.heartEntityId = null;
        setDirty();
        return true;
    }

    public boolean scheduleHeartScavengerWave(UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        long next = Math.max(0L, gameTime);
        if (hearth == null || hearth.heartScavengerNextWaveGameTime == next) {
            return false;
        }
        hearth.heartScavengerNextWaveGameTime = next;
        setDirty();
        return true;
    }

    public boolean markHeartSwarmAnnounced(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.heartSwarmAnnounced) {
            return false;
        }
        hearth.heartSwarmAnnounced = true;
        setDirty();
        return true;
    }

    public boolean bindHeartSuccessor(
            UUID hearthId, UUID entityId, int generation, long assemblyStartGameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || entityId == null) {
            return false;
        }
        hearth.heartSuccessorEntityId = entityId;
        hearth.heartSuccessorGeneration = Math.max(0, generation);
        hearth.heartSuccessorRespawnGameTime = Math.max(0L, assemblyStartGameTime);
        setDirty();
        return true;
    }

    public boolean scheduleHeartSuccessorRespawn(
            UUID hearthId, int generation, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null) {
            return false;
        }
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorGeneration = Math.max(0, generation);
        hearth.heartSuccessorRespawnGameTime = Math.max(0L, gameTime);
        setDirty();
        return true;
    }

    public boolean clearHeartSuccessor(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.heartSuccessorEntityId == null
                && hearth.heartSuccessorRespawnGameTime < 0L)) {
            return false;
        }
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        setDirty();
        return true;
    }

    public boolean setHeartStageForDebug(
            UUID hearthId, long gameTime, com.frozendawn.homo.HeartFormationStage stage) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || stage == com.frozendawn.homo.HeartFormationStage.NONE) {
            return false;
        }
        if (hearth.heartAnchor == null) {
            hearth.heartAnchor = hearth.center.immutable();
        }
        if (hearth.heartLayoutSeed == 0L) {
            hearth.heartLayoutSeed = hearth.layoutSeed ^ hearth.heartAnchor.asLong()
                    ^ 0x48454152544C4154L;
        }
        long elapsed = com.frozendawn.homo.HeartFormationPolicy.elapsedAtStageStart(
                stage, hearth.heartFieldStrength);
        hearth.heartFormationStartGameTime = Math.max(0L, gameTime - elapsed);
        hearth.heartFormationSuppressed = false;
        hearth.heartAdvancementFired = elapsed
                >= com.frozendawn.homo.HeartFormationPolicy.DEAD_AIR_TICKS;
        hearth.heartLive = stage == com.frozendawn.homo.HeartFormationStage.LIVE;
        hearth.heartConvergenceStarted = false;
        hearth.heartMusicActive = true;
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartSwarmAnnounced = false;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        setDirty();
        return true;
    }

    public boolean resetHeartForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.heartAnchor == null
                && hearth.heartFormationStartGameTime < 0L
                && hearth.heartEntityId == null)) {
            return false;
        }
        hearth.heartAnchor = null;
        hearth.heartLayoutSeed = 0L;
        hearth.heartFieldStrength = 0.0F;
        hearth.heartFormationStartGameTime = -1L;
        hearth.heartFormationSuppressed = true;
        hearth.heartAdvancementFired = false;
        hearth.heartLive = false;
        hearth.heartConvergenceStarted = false;
        hearth.heartMusicActive = false;
        hearth.heartEntityId = null;
        hearth.heartDestroyedNodeMask = 0;
        hearth.heartActiveNodeDamage = 0;
        java.util.Arrays.fill(hearth.heartNodeDestroyedGameTimes, -1L);
        hearth.heartDebrisLandedMask = 0;
        hearth.heartCollapseDebrisLanded = false;
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartSwarmAnnounced = false;
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        hearth.heartFragments.clear();
        setDirty();
        return true;
    }

    public boolean completeMasterArchitectStormAftermath(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.masterStormAftermathActive) {
            return false;
        }
        hearth.masterStormAftermathActive = false;
        hearth.hearthStormDead = true;
        setDirty();
        return true;
    }

    public boolean markDecoherenceGranted(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.decoherenceGranted) {
            return false;
        }
        hearth.decoherenceGranted = true;
        setDirty();
        return true;
    }

    public boolean markWatchedStopWatchingGranted(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || hearth.watchedStopWatchingGranted) {
            return false;
        }
        hearth.watchedStopWatchingGranted = true;
        setDirty();
        return true;
    }

    public boolean resetMasterArchitectForDebug(UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (hearth == null || (hearth.masterArchitectEntityId == null
                && !hearth.masterArchitectDefeated
                && hearth.masterArchitectDefeatedGameTime < 0L)) {
            return false;
        }
        hearth.masterArchitectEntityId = null;
        hearth.masterArchitectDefeated = false;
        hearth.masterArchitectDefeatedGameTime = -1L;
        hearth.masterStormAftermathActive = false;
        hearth.masterStormAftermathStartGameTime = -1L;
        hearth.masterStormAftermathStrength = 0.0F;
        hearth.masterStormAftermathKillerId = null;
        hearth.hearthStormDead = false;
        hearth.decoherenceGranted = false;
        hearth.watchedStopWatchingGranted = false;
        hearth.heartAnchor = null;
        hearth.heartLayoutSeed = 0L;
        hearth.heartFieldStrength = 0.0F;
        hearth.heartFormationStartGameTime = -1L;
        hearth.heartFormationSuppressed = false;
        hearth.heartAdvancementFired = false;
        hearth.heartLive = false;
        hearth.heartMusicActive = false;
        hearth.heartEntityId = null;
        hearth.heartDestroyedNodeMask = 0;
        hearth.heartActiveNodeDamage = 0;
        java.util.Arrays.fill(hearth.heartNodeDestroyedGameTimes, -1L);
        hearth.heartDebrisLandedMask = 0;
        hearth.heartCollapseDebrisLanded = false;
        hearth.heartCollapseStartGameTime = -1L;
        hearth.heartCollapseComplete = false;
        hearth.heartMaeveExposed = false;
        resetMaeveErasure(hearth);
        hearth.heartScavengerNextWaveGameTime = -1L;
        hearth.heartSuccessorEntityId = null;
        hearth.heartSuccessorRespawnGameTime = -1L;
        hearth.heartSuccessorGeneration = 0;
        hearth.heartFragments.clear();
        hearth.combatRosterInitialized = false;
        hearth.combatRoster.clear();
        setDirty();
        return true;
    }

    public ContactResult recordPlayerContact(UUID playerId, UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (playerId == null || hearth == null) {
            return ContactResult.noChange();
        }

        long now = Math.max(0L, gameTime);
        PlayerHiveMemory player = playerMemories.computeIfAbsent(playerId,
                id -> new PlayerHiveMemory(id, legacyRelationship));
        ContactUpdate globalUpdate = player.recordContact(now);
        HearthContactMemory local = hearth.playerContacts.computeIfAbsent(playerId,
                HearthContactMemory::new);
        ContactUpdate localUpdate = local.recordContact(now);

        boolean changed = globalUpdate.changed() || localUpdate.changed();
        if (hearth.mood == HearthDisposition.DORMANT) {
            hearth.mood = moodFor(player.relationship, true);
            changed = true;
        }
        if (changed) {
            setDirty();
        }
        return new ContactResult(changed, globalUpdate.firstContact(),
                localUpdate.firstContact(), localUpdate.newVisit());
    }

    public boolean markPlayerSuspicious(UUID playerId, UUID hearthId, long gameTime) {
        return escalateRelationship(playerId, hearthId, gameTime,
                HiveRelationship.SUSPICIOUS);
    }

    public boolean markPlayerOrsathae(UUID playerId, UUID hearthId, long gameTime) {
        return recordHearthViolation(playerId, hearthId, gameTime,
                HearthViolationReason.ENTITY_ATTACK).changed();
    }

    public ViolationResult recordHearthViolation(UUID playerId, UUID hearthId, long gameTime,
                                                 HearthViolationReason reason) {
        HearthRecord origin = hearth(hearthId).orElse(null);
        if (playerId == null || origin == null || reason == null) {
            return ViolationResult.noChange(relationship(playerId));
        }

        long now = Math.max(0L, gameTime);
        recordPlayerContact(playerId, hearthId, now);
        PlayerHiveMemory player = playerMemories.get(playerId);
        HearthContactMemory local = origin.playerContacts.get(playerId);
        HiveRelationship before = player.relationship;
        boolean localReasonRecorded = local != null && local.recordViolation(reason, now);
        boolean changed = localReasonRecorded;
        if (reason == HearthViolationReason.PROTECTED_CONTAINER && !origin.lootTaken) {
            origin.lootTaken = true;
            changed = true;
        }
        changed |= player.escalate(HiveRelationship.ORSATHAE, now, hearthId);
        changed |= recomputeAllHearthConduct();
        if (changed) {
            setDirty();
        }
        return new ViolationResult(changed, localReasonRecorded, before,
                relationship(playerId), reason);
    }

    public boolean clearPlayerViolationsForDebug(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean changed = false;
        for (HearthRecord hearth : hearths) {
            HearthContactMemory local = hearth.playerContacts.get(playerId);
            if (local != null) {
                changed |= local.clearViolations();
            }
            if (hearth.lootTaken) {
                hearth.lootTaken = false;
                changed = true;
            }
        }
        PlayerHiveMemory player = playerMemories.computeIfAbsent(playerId,
                id -> new PlayerHiveMemory(id, legacyRelationship));
        changed |= player.setRelationshipForDebug(HiveRelationship.NEUTRAL, 0L);
        changed |= recomputeAllHearthConduct();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public AssessmentResult recordArchitectAssessment(UUID playerId, UUID hearthId,
                                                       long gameTime, boolean orsaDetected) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        if (playerId == null || hearth == null) {
            return AssessmentResult.noChange(HiveRelationship.NEUTRAL);
        }

        long now = Math.max(0L, gameTime);
        recordPlayerContact(playerId, hearthId, now);
        HearthContactMemory local = hearth.playerContacts.get(playerId);
        HiveRelationship before = relationship(playerId);
        if (local == null || local.architectAssessmentComplete) {
            return AssessmentResult.noChange(before);
        }

        local.architectAssessmentComplete = true;
        local.architectAssessmentGameTime = now;
        local.orsaDetectedAtAssessment = orsaDetected;
        hearth.firstAssessmentFired = true;
        boolean changed = true;

        HiveRelationship desired = HearthArchitectPolicy.relationshipAfterAssessment(
                before, orsaDetected);
        if (desired.ordinal() > before.ordinal()) {
            changed |= escalateRelationship(playerId, hearthId, now, desired);
        }
        if (changed) {
            setDirty();
        }
        return new AssessmentResult(true, orsaDetected, before, relationship(playerId));
    }

    public boolean clearArchitectAssessmentForDebug(UUID playerId, UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        HearthContactMemory local = hearth == null ? null : hearth.playerContacts.get(playerId);
        if (local == null || !local.architectAssessmentComplete) {
            return false;
        }
        local.architectAssessmentComplete = false;
        local.architectAssessmentGameTime = -1L;
        local.orsaDetectedAtAssessment = false;
        hearth.firstAssessmentFired = hearth.playerContacts.values().stream()
                .anyMatch(HearthContactMemory::architectAssessmentComplete);
        setDirty();
        return true;
    }

    public boolean completeFirstTransmission(UUID playerId, UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        HearthContactMemory local = hearth == null ? null : hearth.playerContacts.get(playerId);
        if (local == null || !local.architectAssessmentComplete
                || local.firstTransmissionComplete) {
            return false;
        }
        local.firstTransmissionComplete = true;
        local.firstTransmissionGameTime = Math.max(0L, gameTime);
        hearth.firstTransmissionFired = true;
        setDirty();
        return true;
    }

    public boolean completeHearthMythTransmission(UUID playerId, UUID hearthId, long gameTime) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        HearthContactMemory local = hearth == null ? null : hearth.playerContacts.get(playerId);
        if (local == null || !local.firstTransmissionComplete
                || local.hearthMythTransmissionComplete) {
            return false;
        }
        local.hearthMythTransmissionComplete = true;
        local.hearthMythTransmissionGameTime = Math.max(0L, gameTime);
        setDirty();
        return true;
    }

    public boolean clearFirstTransmissionForDebug(UUID playerId, UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        HearthContactMemory local = hearth == null ? null : hearth.playerContacts.get(playerId);
        if (local == null || (!local.firstTransmissionComplete
                && local.firstTransmissionGameTime < 0L
                && !local.hearthMythTransmissionComplete
                && local.hearthMythTransmissionGameTime < 0L)) {
            return false;
        }
        local.firstTransmissionComplete = false;
        local.firstTransmissionGameTime = -1L;
        local.hearthMythTransmissionComplete = false;
        local.hearthMythTransmissionGameTime = -1L;
        hearth.firstTransmissionFired = hearth.playerContacts.values().stream()
                .anyMatch(HearthContactMemory::firstTransmissionComplete);
        setDirty();
        return true;
    }

    public boolean setRelationshipForDebug(UUID playerId, HiveRelationship relationship,
                                           long gameTime) {
        if (playerId == null || relationship == null) {
            return false;
        }
        PlayerHiveMemory memory = playerMemories.computeIfAbsent(playerId,
                id -> new PlayerHiveMemory(id, legacyRelationship));
        boolean changed = memory.setRelationshipForDebug(relationship, Math.max(0L, gameTime));
        changed |= recomputeAllHearthConduct();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean setHearthMoodForDebug(HearthSelectionPolicy.HearthType type,
                                         HearthDisposition mood) {
        HearthRecord hearth = hearth(type).orElse(null);
        if (hearth == null || mood == null || hearth.mood == mood) {
            return false;
        }
        hearth.mood = mood;
        setDirty();
        return true;
    }

    public int setAllHearthMoodsForDebug(HearthDisposition mood) {
        if (mood == null) {
            return 0;
        }
        int changed = 0;
        for (HearthRecord hearth : hearths) {
            if (hearth.mood != mood) {
                hearth.mood = mood;
                changed++;
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    private boolean escalateRelationship(UUID playerId, UUID hearthId, long gameTime,
                                         HiveRelationship desired) {
        HearthRecord origin = hearth(hearthId).orElse(null);
        if (playerId == null || origin == null) {
            return false;
        }

        long now = Math.max(0L, gameTime);
        recordPlayerContact(playerId, hearthId, now);
        PlayerHiveMemory player = playerMemories.get(playerId);
        boolean changed = player.escalate(desired, now, hearthId);
        changed |= recomputeAllHearthConduct();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private boolean recomputeAllHearthConduct() {
        HiveRelationship strongest = strongestRelationship();
        boolean changed = false;
        for (HearthRecord hearth : hearths) {
            HearthDisposition desiredMood = moodFor(strongest, !hearth.playerContacts.isEmpty());
            ViolationState desiredViolation = violationFor(strongest);
            if (hearth.mood != desiredMood) {
                hearth.mood = desiredMood;
                changed = true;
            }
            if (hearth.violationState != desiredViolation) {
                hearth.violationState = desiredViolation;
                changed = true;
            }
        }
        return changed;
    }

    private HiveRelationship strongestRelationship() {
        HiveRelationship strongest = legacyRelationship;
        for (PlayerHiveMemory memory : playerMemories.values()) {
            if (memory.relationship.ordinal() > strongest.ordinal()) {
                strongest = memory.relationship;
            }
        }
        return strongest;
    }

    private static HearthDisposition moodFor(HiveRelationship relationship, boolean contacted) {
        return switch (relationship) {
            case ORSATHAE -> HearthDisposition.HOSTILE;
            case SUSPICIOUS -> HearthDisposition.AGITATED;
            case NEUTRAL -> contacted ? HearthDisposition.WATCHFUL : HearthDisposition.DORMANT;
        };
    }

    private static ViolationState violationFor(HiveRelationship relationship) {
        return switch (relationship) {
            case ORSATHAE -> ViolationState.VIOLATED;
            case SUSPICIOUS -> ViolationState.SUSPICIOUS;
            case NEUTRAL -> ViolationState.NONE;
        };
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

    private static HiveRelationship loadLegacyRelationship(CompoundTag tag, int storedVersion) {
        if (tag.contains("legacyRelationship", Tag.TAG_STRING)) {
            return readEnum(tag.getString("legacyRelationship"),
                    HiveRelationship.class, HiveRelationship.NEUTRAL);
        }
        if (storedVersion >= 4) {
            return HiveRelationship.NEUTRAL;
        }

        HearthDisposition oldDisposition = readEnum(tag.getString("globalDisposition"),
                HearthDisposition.class, HearthDisposition.DORMANT);
        if (tag.getBoolean("permanentOrsathae") || oldDisposition == HearthDisposition.HOSTILE) {
            return HiveRelationship.ORSATHAE;
        }
        return oldDisposition == HearthDisposition.AGITATED
                ? HiveRelationship.SUSPICIOUS
                : HiveRelationship.NEUTRAL;
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

    public enum HiveRelationship {
        NEUTRAL,
        SUSPICIOUS,
        ORSATHAE
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

    public enum HearthViolationReason {
        ENTITY_ATTACK,
        PROTECTED_ENTRY,
        PROTECTED_DOOR,
        PROTECTED_CONTAINER,
        PROTECTED_BLOCK_BREAK
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

    public record ContactResult(boolean changed, boolean firstGlobalContact,
                                boolean firstHearthContact, boolean newVisit) {
        private static ContactResult noChange() {
            return new ContactResult(false, false, false, false);
        }
    }

    public record DiscoveryResult(boolean changed, boolean newlyDiscovered,
                                  boolean discovered, float signalStrength) {
        private static DiscoveryResult missing() {
            return new DiscoveryResult(false, false, false, 0.0F);
        }
    }

    public record AssessmentResult(boolean completedNow, boolean orsaDetected,
                                   HiveRelationship previousRelationship,
                                   HiveRelationship currentRelationship) {
        private static AssessmentResult noChange(HiveRelationship relationship) {
            return new AssessmentResult(false, false, relationship, relationship);
        }
    }

    public record ViolationResult(boolean changed, boolean localReasonRecorded,
                                  HiveRelationship previousRelationship,
                                  HiveRelationship currentRelationship,
                                  HearthViolationReason reason) {
        private static ViolationResult noChange(HiveRelationship relationship) {
            return new ViolationResult(false, false, relationship, relationship, null);
        }
    }

    private record ContactUpdate(boolean changed, boolean firstContact, boolean newVisit) {
    }

    public static final class PlayerHiveMemory {
        private final UUID playerId;
        private HiveRelationship relationship;
        private long firstContactGameTime = -1L;
        private long lastContactGameTime = -1L;
        private int totalVisits;
        private long relationshipChangedGameTime = -1L;
        private UUID relationshipSourceHearthId;
        private final Set<UUID> congregationCasualtyIds = new LinkedHashSet<>();
        private long lastCongregationCasualtyGameTime = -1L;
        private UUID lastCongregationCasualtyHearthId;

        private PlayerHiveMemory(UUID playerId, HiveRelationship relationship) {
            this.playerId = playerId;
            this.relationship = relationship;
        }

        private static PlayerHiveMemory load(CompoundTag tag) {
            if (!tag.hasUUID("playerId")) {
                return null;
            }
            PlayerHiveMemory memory = new PlayerHiveMemory(tag.getUUID("playerId"),
                    readEnum(tag.getString("relationship"), HiveRelationship.class,
                            HiveRelationship.NEUTRAL));
            memory.firstContactGameTime = readOptionalTime(tag, "firstContactGameTime");
            memory.lastContactGameTime = readOptionalTime(tag, "lastContactGameTime");
            memory.totalVisits = Math.max(0, tag.getInt("totalVisits"));
            memory.relationshipChangedGameTime = readOptionalTime(tag,
                    "relationshipChangedGameTime");
            memory.relationshipSourceHearthId = tag.hasUUID("relationshipSourceHearthId")
                    ? tag.getUUID("relationshipSourceHearthId")
                    : null;
            ListTag casualties = tag.getList(
                    "congregationCasualties", Tag.TAG_COMPOUND);
            for (Tag entry : casualties) {
                if (entry instanceof CompoundTag casualty && casualty.hasUUID("entityId")) {
                    memory.congregationCasualtyIds.add(casualty.getUUID("entityId"));
                }
            }
            memory.lastCongregationCasualtyGameTime = readOptionalTime(
                    tag, "lastCongregationCasualtyGameTime");
            memory.lastCongregationCasualtyHearthId =
                    tag.hasUUID("lastCongregationCasualtyHearthId")
                            ? tag.getUUID("lastCongregationCasualtyHearthId")
                            : null;
            return memory;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);
            tag.putString("relationship", relationship.name());
            tag.putLong("firstContactGameTime", firstContactGameTime);
            tag.putLong("lastContactGameTime", lastContactGameTime);
            tag.putInt("totalVisits", totalVisits);
            tag.putLong("relationshipChangedGameTime", relationshipChangedGameTime);
            if (relationshipSourceHearthId != null) {
                tag.putUUID("relationshipSourceHearthId", relationshipSourceHearthId);
            }
            ListTag casualties = new ListTag();
            for (UUID entityId : congregationCasualtyIds) {
                CompoundTag casualty = new CompoundTag();
                casualty.putUUID("entityId", entityId);
                casualties.add(casualty);
            }
            tag.put("congregationCasualties", casualties);
            tag.putLong("lastCongregationCasualtyGameTime",
                    lastCongregationCasualtyGameTime);
            if (lastCongregationCasualtyHearthId != null) {
                tag.putUUID("lastCongregationCasualtyHearthId",
                        lastCongregationCasualtyHearthId);
            }
            return tag;
        }

        private boolean recordCongregationCasualty(
                UUID entityId, UUID hearthId, long gameTime) {
            if (!congregationCasualtyIds.add(entityId)) {
                return false;
            }
            lastCongregationCasualtyGameTime = gameTime;
            lastCongregationCasualtyHearthId = hearthId;
            return true;
        }

        private ContactUpdate recordContact(long gameTime) {
            boolean first = firstContactGameTime < 0L;
            boolean newVisit = first || lastContactGameTime < 0L
                    || gameTime - lastContactGameTime >= NEW_VISIT_GAP_TICKS;
            boolean shouldPersist = first || newVisit || gameTime < lastContactGameTime
                    || gameTime - lastContactGameTime >= CONTACT_SAVE_INTERVAL_TICKS;
            if (!shouldPersist) {
                return new ContactUpdate(false, false, false);
            }
            if (first) {
                firstContactGameTime = gameTime;
            }
            if (newVisit && totalVisits < Integer.MAX_VALUE) {
                totalVisits++;
            }
            lastContactGameTime = gameTime;
            return new ContactUpdate(true, first, newVisit);
        }

        private boolean escalate(HiveRelationship desired, long gameTime, UUID sourceHearthId) {
            if (desired.ordinal() <= relationship.ordinal()) {
                return false;
            }
            relationship = desired;
            relationshipChangedGameTime = gameTime;
            relationshipSourceHearthId = sourceHearthId;
            return true;
        }

        private boolean setRelationshipForDebug(HiveRelationship desired, long gameTime) {
            if (relationship == desired) {
                return false;
            }
            relationship = desired;
            relationshipChangedGameTime = gameTime;
            if (desired == HiveRelationship.NEUTRAL) {
                relationshipSourceHearthId = null;
            }
            return true;
        }

        public UUID playerId() {
            return playerId;
        }

        public HiveRelationship relationship() {
            return relationship;
        }

        public long firstContactGameTime() {
            return firstContactGameTime;
        }

        public long lastContactGameTime() {
            return lastContactGameTime;
        }

        public int totalVisits() {
            return totalVisits;
        }

        public long relationshipChangedGameTime() {
            return relationshipChangedGameTime;
        }

        public Optional<UUID> relationshipSourceHearthId() {
            return Optional.ofNullable(relationshipSourceHearthId);
        }

        public int congregationCasualties() {
            return congregationCasualtyIds.size();
        }

        public long lastCongregationCasualtyGameTime() {
            return lastCongregationCasualtyGameTime;
        }

        public Optional<UUID> lastCongregationCasualtyHearthId() {
            return Optional.ofNullable(lastCongregationCasualtyHearthId);
        }
    }

    public static final class HearthContactMemory {
        private final UUID playerId;
        private long firstContactGameTime = -1L;
        private long lastContactGameTime = -1L;
        private int visits;
        private boolean attackedWatcher;
        private boolean architectAssessmentComplete;
        private long architectAssessmentGameTime = -1L;
        private boolean orsaDetectedAtAssessment;
        private boolean firstTransmissionComplete;
        private long firstTransmissionGameTime = -1L;
        private boolean hearthMythTransmissionComplete;
        private long hearthMythTransmissionGameTime = -1L;
        private final EnumSet<HearthViolationReason> violationReasons =
                EnumSet.noneOf(HearthViolationReason.class);
        private long firstViolationGameTime = -1L;

        private HearthContactMemory(UUID playerId) {
            this.playerId = playerId;
        }

        private static HearthContactMemory load(CompoundTag tag) {
            if (!tag.hasUUID("playerId")) {
                return null;
            }
            HearthContactMemory memory = new HearthContactMemory(tag.getUUID("playerId"));
            memory.firstContactGameTime = readOptionalTime(tag, "firstContactGameTime");
            memory.lastContactGameTime = readOptionalTime(tag, "lastContactGameTime");
            memory.visits = Math.max(0, tag.getInt("visits"));
            memory.attackedWatcher = tag.getBoolean("attackedWatcher");
            memory.architectAssessmentComplete = tag.getBoolean("architectAssessmentComplete");
            memory.architectAssessmentGameTime = readOptionalTime(
                    tag, "architectAssessmentGameTime");
            memory.orsaDetectedAtAssessment = tag.getBoolean("orsaDetectedAtAssessment");
            memory.firstTransmissionComplete = tag.getBoolean("firstTransmissionComplete");
            memory.firstTransmissionGameTime = readOptionalTime(
                    tag, "firstTransmissionGameTime");
            memory.hearthMythTransmissionComplete = tag.getBoolean(
                    "hearthMythTransmissionComplete");
            memory.hearthMythTransmissionGameTime = readOptionalTime(
                    tag, "hearthMythTransmissionGameTime");
            ListTag violationList = tag.getList("violationReasons", Tag.TAG_STRING);
            for (Tag entry : violationList) {
                HearthViolationReason reason = readEnum(entry.getAsString(),
                        HearthViolationReason.class, null);
                if (reason != null) {
                    memory.violationReasons.add(reason);
                }
            }
            if (memory.attackedWatcher && memory.violationReasons.isEmpty()) {
                memory.violationReasons.add(HearthViolationReason.ENTITY_ATTACK);
            }
            memory.firstViolationGameTime = readOptionalTime(tag, "firstViolationGameTime");
            return memory;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);
            tag.putLong("firstContactGameTime", firstContactGameTime);
            tag.putLong("lastContactGameTime", lastContactGameTime);
            tag.putInt("visits", visits);
            tag.putBoolean("attackedWatcher", attackedWatcher);
            tag.putBoolean("architectAssessmentComplete", architectAssessmentComplete);
            tag.putLong("architectAssessmentGameTime", architectAssessmentGameTime);
            tag.putBoolean("orsaDetectedAtAssessment", orsaDetectedAtAssessment);
            tag.putBoolean("firstTransmissionComplete", firstTransmissionComplete);
            tag.putLong("firstTransmissionGameTime", firstTransmissionGameTime);
            tag.putBoolean("hearthMythTransmissionComplete", hearthMythTransmissionComplete);
            tag.putLong("hearthMythTransmissionGameTime", hearthMythTransmissionGameTime);
            ListTag violationList = new ListTag();
            for (HearthViolationReason reason : violationReasons) {
                violationList.add(StringTag.valueOf(reason.name()));
            }
            tag.put("violationReasons", violationList);
            tag.putLong("firstViolationGameTime", firstViolationGameTime);
            return tag;
        }

        private ContactUpdate recordContact(long gameTime) {
            boolean first = firstContactGameTime < 0L;
            boolean newVisit = first || lastContactGameTime < 0L
                    || gameTime - lastContactGameTime >= NEW_VISIT_GAP_TICKS;
            boolean shouldPersist = first || newVisit || gameTime < lastContactGameTime
                    || gameTime - lastContactGameTime >= CONTACT_SAVE_INTERVAL_TICKS;
            if (!shouldPersist) {
                return new ContactUpdate(false, false, false);
            }
            if (first) {
                firstContactGameTime = gameTime;
            }
            if (newVisit && visits < Integer.MAX_VALUE) {
                visits++;
            }
            lastContactGameTime = gameTime;
            return new ContactUpdate(true, first, newVisit);
        }

        private boolean recordViolation(HearthViolationReason reason, long gameTime) {
            boolean changed = violationReasons.add(reason);
            if (reason == HearthViolationReason.ENTITY_ATTACK && !attackedWatcher) {
                attackedWatcher = true;
                changed = true;
            }
            if (firstViolationGameTime < 0L) {
                firstViolationGameTime = gameTime;
                changed = true;
            }
            return changed;
        }

        private boolean clearViolations() {
            if (violationReasons.isEmpty() && !attackedWatcher
                    && firstViolationGameTime < 0L) {
                return false;
            }
            violationReasons.clear();
            attackedWatcher = false;
            firstViolationGameTime = -1L;
            return true;
        }

        public UUID playerId() {
            return playerId;
        }

        public long firstContactGameTime() {
            return firstContactGameTime;
        }

        public long lastContactGameTime() {
            return lastContactGameTime;
        }

        public int visits() {
            return visits;
        }

        public boolean attackedWatcher() {
            return attackedWatcher;
        }

        public boolean architectAssessmentComplete() {
            return architectAssessmentComplete;
        }

        public long architectAssessmentGameTime() {
            return architectAssessmentGameTime;
        }

        public boolean orsaDetectedAtAssessment() {
            return orsaDetectedAtAssessment;
        }

        public boolean firstTransmissionComplete() {
            return firstTransmissionComplete;
        }

        public long firstTransmissionGameTime() {
            return firstTransmissionGameTime;
        }

        public boolean hearthMythTransmissionComplete() {
            return hearthMythTransmissionComplete;
        }

        public long hearthMythTransmissionGameTime() {
            return hearthMythTransmissionGameTime;
        }

        public Set<HearthViolationReason> violationReasons() {
            return Set.copyOf(violationReasons);
        }

        public long firstViolationGameTime() {
            return firstViolationGameTime;
        }
    }

    private static long readOptionalTime(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : -1L;
    }

    public record HeartFragmentSnapshot(BlockPos relativePos, String blockId) {
        public HeartFragmentSnapshot {
            relativePos = relativePos == null ? BlockPos.ZERO : relativePos.immutable();
            blockId = blockId == null || blockId.isBlank() ? "minecraft:packed_ice" : blockId;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("relativePos", relativePos.asLong());
            tag.putString("blockId", blockId);
            return tag;
        }

        private static HeartFragmentSnapshot load(CompoundTag tag) {
            if (!tag.contains("relativePos", Tag.TAG_LONG)) {
                return null;
            }
            return new HeartFragmentSnapshot(
                    BlockPos.of(tag.getLong("relativePos")), tag.getString("blockId"));
        }
    }

    public record HeartNodeDamageResult(
            boolean accepted,
            boolean destroyed,
            int activeDamage,
            int destroyedMask) {
        private static HeartNodeDamageResult rejected() {
            return new HeartNodeDamageResult(false, false, 0, 0);
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
        private final Set<Integer> structureDegradedCursors = new TreeSet<>();
        private int structureReconcileAttempts;
        private boolean structureDegradedAccepted;
        private float signalStrength;
        private String boundVariantProfile;
        private boolean watcherSpawned;
        private UUID watcherEntityId;
        private boolean architectAssessorSpawned;
        private UUID architectAssessorEntityId;
        private String architectAssessorProfile;
        private final Map<HearthPopulationRole, HearthResidentBinding> populationResidents =
                new EnumMap<>(HearthPopulationRole.class);
        private UUID masterArchitectEntityId;
        private boolean masterArchitectDefeated;
        private long masterArchitectDefeatedGameTime = -1L;
        private boolean masterStormAftermathActive;
        private long masterStormAftermathStartGameTime = -1L;
        private float masterStormAftermathStrength;
        private UUID masterStormAftermathKillerId;
        private boolean hearthStormDead;
        private boolean decoherenceGranted;
        private boolean watchedStopWatchingGranted;
        private BlockPos heartAnchor;
        private long heartLayoutSeed;
        private float heartFieldStrength;
        private long heartFormationStartGameTime = -1L;
        private boolean heartFormationSuppressed;
        private boolean heartAdvancementFired;
        private boolean heartLive;
        private boolean heartConvergenceStarted;
        private boolean heartMusicActive;
        private UUID heartEntityId;
        private int heartDestroyedNodeMask;
        private int heartActiveNodeDamage;
        private final long[] heartNodeDestroyedGameTimes = {
                -1L, -1L, -1L, -1L, -1L
        };
        private int heartDebrisLandedMask;
        private boolean heartCollapseDebrisLanded;
        private long heartCollapseStartGameTime = -1L;
        private boolean heartCollapseComplete;
        private boolean heartMaeveExposed;
        private long heartMaeveErasureStartGameTime = -1L;
        private boolean heartMaeveErasureComplete;
        private UUID heartMaeveEraserId;
        private boolean heartMaeveForgeAnnounced;
        private int heartMaeveAftermathSoundMask;
        private boolean heartMaeveWorldMessageShown;
        private boolean heartMaeveCollapseResponsePlayed;
        private boolean heartMaeveBiologicalWarningPlayed;
        private long heartMaeveBiologicalWarningGameTime = -1L;
        private boolean heartLastWitnessDropped;
        private boolean heartFinalAdvancementGranted;
        private boolean heartSwarmAnnounced;
        private long heartScavengerNextWaveGameTime = -1L;
        private UUID heartSuccessorEntityId;
        private long heartSuccessorRespawnGameTime = -1L;
        private int heartSuccessorGeneration;
        private final List<HeartFragmentSnapshot> heartFragments = new ArrayList<>();
        private long lastPlayerContactGameTime;
        private boolean firstAssessmentFired;
        private boolean firstTransmissionFired;
        private boolean lootTaken;
        private boolean combatRosterInitialized;
        private final Map<UUID, HearthEncounterRole> combatRoster = new LinkedHashMap<>();
        private final Map<UUID, HearthContactMemory> playerContacts = new LinkedHashMap<>();

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
            this.architectAssessorProfile = "";
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
            record.structureDegradedCursors.clear();
            for (int degraded : tag.getIntArray("structureDegradedCursors")) {
                if (degraded >= 0) {
                    record.structureDegradedCursors.add(degraded);
                }
            }
            record.structureReconcileAttempts =
                    Math.max(0, tag.getInt("structureReconcileAttempts"));
            record.structureDegradedAccepted = tag.getBoolean("structureDegradedAccepted");
            record.signalStrength = Math.max(0.0F, tag.getFloat("signalStrength"));
            record.boundVariantProfile = tag.getString("boundVariantProfile");
            record.watcherSpawned = tag.getBoolean("watcherSpawned");
            record.watcherEntityId = tag.hasUUID("watcherEntityId")
                    ? tag.getUUID("watcherEntityId")
                    : null;
            record.architectAssessorSpawned = tag.getBoolean("architectAssessorSpawned");
            record.architectAssessorEntityId = tag.hasUUID("architectAssessorEntityId")
                    ? tag.getUUID("architectAssessorEntityId")
                    : null;
            record.architectAssessorProfile = tag.getString("architectAssessorProfile");
            ListTag residents = tag.getList("populationResidents", Tag.TAG_COMPOUND);
            for (Tag entry : residents) {
                if (!(entry instanceof CompoundTag compound)) {
                    continue;
                }
                HearthResidentBinding binding = HearthResidentBinding.load(compound);
                if (binding != null) {
                    record.populationResidents.putIfAbsent(binding.role(), binding);
                }
            }
            record.masterArchitectEntityId = tag.hasUUID("masterArchitectEntityId")
                    ? tag.getUUID("masterArchitectEntityId")
                    : null;
            record.masterArchitectDefeated = tag.getBoolean("masterArchitectDefeated");
            record.masterArchitectDefeatedGameTime = readOptionalTime(
                    tag, "masterArchitectDefeatedGameTime");
            record.masterStormAftermathActive = tag.getBoolean("masterStormAftermathActive");
            record.masterStormAftermathStartGameTime = readOptionalTime(
                    tag, "masterStormAftermathStartGameTime");
            record.masterStormAftermathStrength = Math.max(
                    0.0F, Math.min(1.0F, tag.getFloat("masterStormAftermathStrength")));
            record.masterStormAftermathKillerId = tag.hasUUID("masterStormAftermathKillerId")
                    ? tag.getUUID("masterStormAftermathKillerId") : null;
            record.hearthStormDead = tag.getBoolean("hearthStormDead");
            record.decoherenceGranted = tag.getBoolean("decoherenceGranted");
            record.watchedStopWatchingGranted =
                    tag.contains("watchedStopWatchingGranted", Tag.TAG_BYTE)
                            ? tag.getBoolean("watchedStopWatchingGranted")
                            : record.hearthStormDead && record.decoherenceGranted;
            record.heartAnchor = tag.contains("heartAnchor", Tag.TAG_LONG)
                    ? BlockPos.of(tag.getLong("heartAnchor")) : null;
            record.heartLayoutSeed = tag.getLong("heartLayoutSeed");
            record.heartFieldStrength = Math.max(0.0F,
                    Math.min(1.0F, tag.getFloat("heartFieldStrength")));
            record.heartFormationStartGameTime = readOptionalTime(
                    tag, "heartFormationStartGameTime");
            record.heartFormationSuppressed = tag.getBoolean("heartFormationSuppressed");
            record.heartAdvancementFired = tag.getBoolean("heartAdvancementFired");
            record.heartLive = tag.getBoolean("heartLive");
            record.heartConvergenceStarted = tag.getBoolean("heartConvergenceStarted");
            record.heartMusicActive = tag.contains("heartMusicActive", Tag.TAG_BYTE)
                    ? tag.getBoolean("heartMusicActive")
                    : record.heartFormationStartGameTime >= 0L
                    && !record.heartFormationSuppressed;
            record.heartEntityId = tag.hasUUID("heartEntityId")
                    ? tag.getUUID("heartEntityId") : null;
            record.heartDestroyedNodeMask = tag.getInt("heartDestroyedNodeMask")
                    & ((1 << com.frozendawn.homo.HeartLattice.NODE_COUNT) - 1);
            record.heartActiveNodeDamage = Math.max(0, Math.min(
                    com.frozendawn.homo.HeartLattice.HITS_PER_NODE - 1,
                    tag.getInt("heartActiveNodeDamage")));
            if (com.frozendawn.homo.HeartLattice.nextNode(
                    record.heartDestroyedNodeMask) < 0) {
                record.heartActiveNodeDamage = 0;
            }
            long[] nodeTimes = tag.getLongArray("heartNodeDestroyedGameTimes");
            System.arraycopy(nodeTimes, 0, record.heartNodeDestroyedGameTimes, 0,
                    Math.min(nodeTimes.length, record.heartNodeDestroyedGameTimes.length));
            record.heartDebrisLandedMask = tag.getInt("heartDebrisLandedMask")
                    & ((1 << com.frozendawn.homo.HeartLattice.NODE_COUNT) - 1);
            record.heartCollapseDebrisLanded =
                    tag.getBoolean("heartCollapseDebrisLanded");
            record.heartCollapseStartGameTime = readOptionalTime(
                    tag, "heartCollapseStartGameTime");
            record.heartCollapseComplete = tag.getBoolean("heartCollapseComplete");
            record.heartMaeveExposed = tag.getBoolean("heartMaeveExposed");
            record.heartMaeveErasureStartGameTime = readOptionalTime(
                    tag, "heartMaeveErasureStartGameTime");
            record.heartMaeveErasureComplete =
                    tag.getBoolean("heartMaeveErasureComplete");
            record.heartMaeveEraserId = tag.hasUUID("heartMaeveEraserId")
                    ? tag.getUUID("heartMaeveEraserId") : null;
            record.heartMaeveForgeAnnounced =
                    tag.getBoolean("heartMaeveForgeAnnounced");
            record.heartMaeveAftermathSoundMask =
                    tag.getInt("heartMaeveAftermathSoundMask") & 0b11111;
            record.heartMaeveWorldMessageShown =
                    tag.getBoolean("heartMaeveWorldMessageShown");
            record.heartMaeveCollapseResponsePlayed = tag.contains(
                    "heartMaeveCollapseResponsePlayed", Tag.TAG_BYTE)
                    ? tag.getBoolean("heartMaeveCollapseResponsePlayed")
                    : record.heartMaeveWorldMessageShown;
            record.heartMaeveBiologicalWarningPlayed = tag.contains(
                    "heartMaeveBiologicalWarningPlayed", Tag.TAG_BYTE)
                    ? tag.getBoolean("heartMaeveBiologicalWarningPlayed")
                    : record.heartMaeveWorldMessageShown;
            record.heartMaeveBiologicalWarningGameTime = readOptionalTime(
                    tag, "heartMaeveBiologicalWarningGameTime");
            if (record.heartMaeveErasureComplete
                    && !tag.contains("heartMaeveWorldMessageShown")) {
                record.heartMaeveAftermathSoundMask = 0b11111;
                record.heartMaeveWorldMessageShown = true;
                record.heartMaeveCollapseResponsePlayed = true;
                record.heartMaeveBiologicalWarningPlayed = true;
            }
            record.heartLastWitnessDropped =
                    tag.getBoolean("heartLastWitnessDropped");
            record.heartFinalAdvancementGranted =
                    tag.getBoolean("heartFinalAdvancementGranted");
            record.heartSwarmAnnounced = tag.getBoolean("heartSwarmAnnounced");
            record.heartScavengerNextWaveGameTime = readOptionalTime(
                    tag, "heartScavengerNextWaveGameTime");
            record.heartSuccessorEntityId = tag.hasUUID("heartSuccessorEntityId")
                    ? tag.getUUID("heartSuccessorEntityId") : null;
            record.heartSuccessorRespawnGameTime = readOptionalTime(
                    tag, "heartSuccessorRespawnGameTime");
            record.heartSuccessorGeneration = Math.max(0,
                    tag.getInt("heartSuccessorGeneration"));
            ListTag heartFragments = tag.getList("heartFragments", Tag.TAG_COMPOUND);
            for (Tag entry : heartFragments) {
                if (entry instanceof CompoundTag fragmentTag) {
                    HeartFragmentSnapshot fragment = HeartFragmentSnapshot.load(fragmentTag);
                    if (fragment != null && record.heartFragments.size() < 40) {
                        record.heartFragments.add(fragment);
                    }
                }
            }
            record.lastPlayerContactGameTime = tag.contains("lastPlayerContactGameTime", Tag.TAG_LONG)
                    ? tag.getLong("lastPlayerContactGameTime")
                    : -1L;
            record.firstAssessmentFired = tag.getBoolean("firstAssessmentFired");
            record.firstTransmissionFired = tag.getBoolean("firstTransmissionFired");
            record.lootTaken = tag.getBoolean("lootTaken");
            record.combatRosterInitialized = tag.getBoolean("combatRosterInitialized");
            ListTag roster = tag.getList("combatRoster", Tag.TAG_COMPOUND);
            for (Tag entry : roster) {
                if (!(entry instanceof CompoundTag member) || !member.hasUUID("entityId")) {
                    continue;
                }
                HearthEncounterRole role = HearthEncounterRole.fromSerializedName(
                        member.getString("role"));
                if (role == HearthEncounterRole.BYSTANDER
                        && !record.masterArchitectDefeated) {
                    role = HearthEncounterRole.RESERVED;
                }
                if (role != HearthEncounterRole.UNASSIGNED) {
                    record.combatRoster.putIfAbsent(member.getUUID("entityId"), role);
                }
            }
            ListTag contacts = tag.getList("playerContacts", Tag.TAG_COMPOUND);
            for (Tag entry : contacts) {
                if (!(entry instanceof CompoundTag compound)) {
                    continue;
                }
                HearthContactMemory memory = HearthContactMemory.load(compound);
                if (memory != null) {
                    record.playerContacts.putIfAbsent(memory.playerId(), memory);
                }
            }
            return record;
        }

        private void migrateHeartState(int storedVersion) {
            if (storedVersion < 17 && com.frozendawn.homo.HeartLattice.nextNode(
                    heartDestroyedNodeMask) < 0) {
                heartCollapseStartGameTime = 0L;
                heartCollapseComplete = true;
                heartMaeveExposed = true;
                heartLive = false;
                heartActiveNodeDamage = 0;
            }
            if (storedVersion < 18) {
                for (int node = 0; node < heartNodeDestroyedGameTimes.length; node++) {
                    if ((heartDestroyedNodeMask & 1 << node) != 0) {
                        heartNodeDestroyedGameTimes[node] = 0L;
                    }
                }
                heartDebrisLandedMask = heartDestroyedNodeMask;
                heartCollapseDebrisLanded = heartCollapseComplete;
            }
            if (storedVersion < 19) {
                heartScavengerNextWaveGameTime = -1L;
                heartSuccessorEntityId = null;
                heartSuccessorRespawnGameTime = -1L;
                heartSuccessorGeneration = 0;
            }
            if (heartMaeveErasureComplete) {
                heartMaeveExposed = false;
                heartLive = false;
                heartMusicActive = false;
                heartEntityId = null;
                heartScavengerNextWaveGameTime = -1L;
                heartSuccessorEntityId = null;
                heartSuccessorRespawnGameTime = -1L;
            }
            if (storedVersion < 20) {
                heartSwarmAnnounced = false;
            }
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
            tag.putIntArray("structureDegradedCursors",
                    structureDegradedCursors.stream().mapToInt(Integer::intValue).toArray());
            tag.putInt("structureReconcileAttempts", structureReconcileAttempts);
            tag.putBoolean("structureDegradedAccepted", structureDegradedAccepted);
            tag.putFloat("signalStrength", signalStrength);
            tag.putString("boundVariantProfile", boundVariantProfile);
            tag.putBoolean("watcherSpawned", watcherSpawned);
            if (watcherEntityId != null) {
                tag.putUUID("watcherEntityId", watcherEntityId);
            }
            tag.putBoolean("architectAssessorSpawned", architectAssessorSpawned);
            if (architectAssessorEntityId != null) {
                tag.putUUID("architectAssessorEntityId", architectAssessorEntityId);
            }
            tag.putString("architectAssessorProfile", architectAssessorProfile);
            ListTag residents = new ListTag();
            for (HearthResidentBinding binding : populationResidents.values()) {
                residents.add(binding.save());
            }
            tag.put("populationResidents", residents);
            if (masterArchitectEntityId != null) {
                tag.putUUID("masterArchitectEntityId", masterArchitectEntityId);
            }
            tag.putBoolean("masterArchitectDefeated", masterArchitectDefeated);
            tag.putLong("masterArchitectDefeatedGameTime", masterArchitectDefeatedGameTime);
            tag.putBoolean("masterStormAftermathActive", masterStormAftermathActive);
            tag.putLong("masterStormAftermathStartGameTime",
                    masterStormAftermathStartGameTime);
            tag.putFloat("masterStormAftermathStrength", masterStormAftermathStrength);
            if (masterStormAftermathKillerId != null) {
                tag.putUUID("masterStormAftermathKillerId", masterStormAftermathKillerId);
            }
            tag.putBoolean("hearthStormDead", hearthStormDead);
            tag.putBoolean("decoherenceGranted", decoherenceGranted);
            tag.putBoolean("watchedStopWatchingGranted", watchedStopWatchingGranted);
            if (heartAnchor != null) {
                tag.putLong("heartAnchor", heartAnchor.asLong());
            }
            tag.putLong("heartLayoutSeed", heartLayoutSeed);
            tag.putFloat("heartFieldStrength", heartFieldStrength);
            tag.putLong("heartFormationStartGameTime", heartFormationStartGameTime);
            tag.putBoolean("heartFormationSuppressed", heartFormationSuppressed);
            tag.putBoolean("heartAdvancementFired", heartAdvancementFired);
            tag.putBoolean("heartLive", heartLive);
            tag.putBoolean("heartConvergenceStarted", heartConvergenceStarted);
            tag.putBoolean("heartMusicActive", heartMusicActive);
            if (heartEntityId != null) {
                tag.putUUID("heartEntityId", heartEntityId);
            }
            tag.putInt("heartDestroyedNodeMask", heartDestroyedNodeMask);
            tag.putInt("heartActiveNodeDamage", heartActiveNodeDamage);
            tag.putLongArray("heartNodeDestroyedGameTimes", heartNodeDestroyedGameTimes);
            tag.putInt("heartDebrisLandedMask", heartDebrisLandedMask);
            tag.putBoolean("heartCollapseDebrisLanded", heartCollapseDebrisLanded);
            tag.putLong("heartCollapseStartGameTime", heartCollapseStartGameTime);
            tag.putBoolean("heartCollapseComplete", heartCollapseComplete);
            tag.putBoolean("heartMaeveExposed", heartMaeveExposed);
            tag.putLong("heartMaeveErasureStartGameTime",
                    heartMaeveErasureStartGameTime);
            tag.putBoolean("heartMaeveErasureComplete",
                    heartMaeveErasureComplete);
            if (heartMaeveEraserId != null) {
                tag.putUUID("heartMaeveEraserId", heartMaeveEraserId);
            }
            tag.putBoolean("heartMaeveForgeAnnounced", heartMaeveForgeAnnounced);
            tag.putInt("heartMaeveAftermathSoundMask",
                    heartMaeveAftermathSoundMask);
            tag.putBoolean("heartMaeveWorldMessageShown",
                    heartMaeveWorldMessageShown);
            tag.putBoolean("heartMaeveCollapseResponsePlayed",
                    heartMaeveCollapseResponsePlayed);
            tag.putBoolean("heartMaeveBiologicalWarningPlayed",
                    heartMaeveBiologicalWarningPlayed);
            tag.putLong("heartMaeveBiologicalWarningGameTime",
                    heartMaeveBiologicalWarningGameTime);
            tag.putBoolean("heartLastWitnessDropped", heartLastWitnessDropped);
            tag.putBoolean("heartFinalAdvancementGranted",
                    heartFinalAdvancementGranted);
            tag.putBoolean("heartSwarmAnnounced", heartSwarmAnnounced);
            tag.putLong("heartScavengerNextWaveGameTime",
                    heartScavengerNextWaveGameTime);
            if (heartSuccessorEntityId != null) {
                tag.putUUID("heartSuccessorEntityId", heartSuccessorEntityId);
            }
            tag.putLong("heartSuccessorRespawnGameTime",
                    heartSuccessorRespawnGameTime);
            tag.putInt("heartSuccessorGeneration", heartSuccessorGeneration);
            ListTag heartFragments = new ListTag();
            for (HeartFragmentSnapshot fragment : this.heartFragments) {
                heartFragments.add(fragment.save());
            }
            tag.put("heartFragments", heartFragments);
            tag.putLong("lastPlayerContactGameTime", lastPlayerContactGameTime);
            tag.putBoolean("firstAssessmentFired", firstAssessmentFired);
            tag.putBoolean("firstTransmissionFired", firstTransmissionFired);
            tag.putBoolean("lootTaken", lootTaken);
            tag.putBoolean("combatRosterInitialized", combatRosterInitialized);
            ListTag roster = new ListTag();
            for (Map.Entry<UUID, HearthEncounterRole> entry : combatRoster.entrySet()) {
                CompoundTag member = new CompoundTag();
                member.putUUID("entityId", entry.getKey());
                member.putString("role", entry.getValue().serializedName());
                roster.add(member);
            }
            tag.put("combatRoster", roster);
            ListTag contacts = new ListTag();
            for (HearthContactMemory memory : playerContacts.values()) {
                contacts.add(memory.save());
            }
            tag.put("playerContacts", contacts);
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

        /** Cursors whose structural piece could not be placed and still needs another pass. */
        public Set<Integer> structureDegradedCursors() {
            return Set.copyOf(structureDegradedCursors);
        }

        public boolean hasDegradedPlacements() {
            return !structureDegradedCursors.isEmpty();
        }

        public OptionalInt lowestDegradedCursor() {
            return structureDegradedCursors.stream().mapToInt(Integer::intValue).min();
        }

        /** Completed passes that ended with unresolved structural placements. */
        public int structureReconcileAttempts() {
            return structureReconcileAttempts;
        }

        /** True once the scene was allowed to finish with holes we stopped retrying. */
        public boolean structureDegradedAccepted() {
            return structureDegradedAccepted;
        }

        public float signalStrength() {
            return signalStrength;
        }

        public String boundVariantProfile() {
            return boundVariantProfile;
        }

        public boolean watcherSpawned() {
            return watcherSpawned;
        }

        public Optional<UUID> watcherEntityId() {
            return Optional.ofNullable(watcherEntityId);
        }

        public boolean architectAssessorSpawned() {
            return architectAssessorSpawned;
        }

        public Optional<UUID> architectAssessorEntityId() {
            return Optional.ofNullable(architectAssessorEntityId);
        }

        public String architectAssessorProfile() {
            return architectAssessorProfile;
        }

        public Optional<HearthResidentBinding> populationResident(HearthPopulationRole role) {
            return Optional.ofNullable(populationResidents.get(role));
        }

        public List<HearthResidentBinding> populationResidents() {
            return List.copyOf(populationResidents.values());
        }

        public Optional<UUID> masterArchitectEntityId() {
            return Optional.ofNullable(masterArchitectEntityId);
        }

        public boolean masterArchitectDefeated() {
            return masterArchitectDefeated;
        }

        public long masterArchitectDefeatedGameTime() {
            return masterArchitectDefeatedGameTime;
        }

        public boolean masterStormAftermathActive() {
            return masterStormAftermathActive;
        }

        public long masterStormAftermathStartGameTime() {
            return masterStormAftermathStartGameTime;
        }

        public float masterStormAftermathStrength() {
            return masterStormAftermathStrength;
        }

        public Optional<UUID> masterStormAftermathKillerId() {
            return Optional.ofNullable(masterStormAftermathKillerId);
        }

        public boolean hearthStormDead() {
            return hearthStormDead;
        }

        public boolean decoherenceGranted() {
            return decoherenceGranted;
        }

        public boolean watchedStopWatchingGranted() {
            return watchedStopWatchingGranted;
        }

        public Optional<BlockPos> heartAnchor() {
            return Optional.ofNullable(heartAnchor);
        }

        public long heartLayoutSeed() {
            return heartLayoutSeed;
        }

        public float heartFieldStrength() {
            return heartFieldStrength;
        }

        public long heartFormationStartGameTime() {
            return heartFormationStartGameTime;
        }

        public boolean heartFormationSuppressed() {
            return heartFormationSuppressed;
        }

        public boolean heartAdvancementFired() {
            return heartAdvancementFired;
        }

        public boolean heartLive() {
            return heartLive;
        }

        public boolean heartConvergenceStarted() {
            return heartConvergenceStarted;
        }

        public boolean heartMusicActive() {
            return heartMusicActive;
        }

        public Optional<UUID> heartEntityId() {
            return Optional.ofNullable(heartEntityId);
        }

        public int heartDestroyedNodeMask() {
            return heartDestroyedNodeMask;
        }

        public int heartActiveNodeDamage() {
            return heartActiveNodeDamage;
        }

        public long heartNodeDestroyedGameTime(int nodeIndex) {
            return nodeIndex >= 0 && nodeIndex < heartNodeDestroyedGameTimes.length
                    ? heartNodeDestroyedGameTimes[nodeIndex] : -1L;
        }

        public int heartDebrisLandedMask() {
            return heartDebrisLandedMask;
        }

        public boolean heartCollapseDebrisLanded() {
            return heartCollapseDebrisLanded;
        }

        public long heartCollapseStartGameTime() {
            return heartCollapseStartGameTime;
        }

        public boolean heartCollapseComplete() {
            return heartCollapseComplete;
        }

        public boolean heartMaeveExposed() {
            return heartMaeveExposed;
        }

        public long heartMaeveErasureStartGameTime() {
            return heartMaeveErasureStartGameTime;
        }

        public boolean heartMaeveErasureComplete() {
            return heartMaeveErasureComplete;
        }

        public Optional<UUID> heartMaeveEraserId() {
            return Optional.ofNullable(heartMaeveEraserId);
        }

        public boolean heartMaeveForgeAnnounced() {
            return heartMaeveForgeAnnounced;
        }

        public int heartMaeveAftermathSoundMask() {
            return heartMaeveAftermathSoundMask;
        }

        public boolean heartMaeveWorldMessageShown() {
            return heartMaeveWorldMessageShown;
        }

        public boolean heartMaeveBiologicalWarningPlayed() {
            return heartMaeveBiologicalWarningPlayed;
        }

        public long heartMaeveBiologicalWarningGameTime() {
            return heartMaeveBiologicalWarningGameTime;
        }

        public boolean heartLastWitnessDropped() {
            return heartLastWitnessDropped;
        }

        public boolean heartFinalAdvancementGranted() {
            return heartFinalAdvancementGranted;
        }

        public boolean heartSwarmAnnounced() {
            return heartSwarmAnnounced;
        }

        public long heartScavengerNextWaveGameTime() {
            return heartScavengerNextWaveGameTime;
        }

        public Optional<UUID> heartSuccessorEntityId() {
            return Optional.ofNullable(heartSuccessorEntityId);
        }

        public long heartSuccessorRespawnGameTime() {
            return heartSuccessorRespawnGameTime;
        }

        public int heartSuccessorGeneration() {
            return heartSuccessorGeneration;
        }

        public List<HeartFragmentSnapshot> heartFragments() {
            return List.copyOf(heartFragments);
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

        public boolean combatRosterInitialized() {
            return combatRosterInitialized;
        }

        public Map<UUID, HearthEncounterRole> combatRoster() {
            return Map.copyOf(combatRoster);
        }

        public Optional<HearthContactMemory> playerContact(UUID playerId) {
            return Optional.ofNullable(playerContacts.get(playerId));
        }

        public List<HearthContactMemory> playerContacts() {
            return List.copyOf(playerContacts.values());
        }
    }

    public static final class HearthResidentBinding {
        private final HearthPopulationRole role;
        private UUID entityId;
        private long respawnAfterGameTime = -1L;
        private boolean permanentlyVacant;

        private HearthResidentBinding(HearthPopulationRole role) {
            this.role = role;
        }

        private static HearthResidentBinding load(CompoundTag tag) {
            HearthPopulationRole role = HearthPopulationRole.fromSerializedName(
                    tag.getString("role"));
            if (role == null) {
                return null;
            }
            HearthResidentBinding binding = new HearthResidentBinding(role);
            binding.entityId = tag.hasUUID("entityId") ? tag.getUUID("entityId") : null;
            binding.respawnAfterGameTime = tag.contains("respawnAfterGameTime", Tag.TAG_LONG)
                    ? tag.getLong("respawnAfterGameTime")
                    : -1L;
            binding.permanentlyVacant = tag.getBoolean("permanentlyVacant");
            return binding;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("role", role.serializedName());
            if (entityId != null) {
                tag.putUUID("entityId", entityId);
            }
            tag.putLong("respawnAfterGameTime", respawnAfterGameTime);
            tag.putBoolean("permanentlyVacant", permanentlyVacant);
            return tag;
        }

        public HearthPopulationRole role() {
            return role;
        }

        public Optional<UUID> entityId() {
            return Optional.ofNullable(entityId);
        }

        public long respawnAfterGameTime() {
            return respawnAfterGameTime;
        }

        public boolean permanentlyVacant() {
            return permanentlyVacant;
        }
    }
}
