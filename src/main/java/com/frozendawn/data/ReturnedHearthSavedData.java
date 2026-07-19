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
import java.util.Set;
import java.util.UUID;

/**
 * Persistent world-level identity and maturation records for Returned Hearth sites.
 *
 * Physical reconciliation progress is stored here so bounded placement can resume
 * after chunk unloads or server restarts without duplicating scene pieces.
 */
public final class ReturnedHearthSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 10;
    public static final long CONTACT_SAVE_INTERVAL_TICKS = 200L;
    public static final long NEW_VISIT_GAP_TICKS = 1_200L;

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_returned_hearths";

    private int dataVersion = CURRENT_DATA_VERSION;
    private BlockPos transponderAnchor;
    private boolean selectionComplete;
    private long selectionGameTime = -1L;
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
                state.hearths.add(record);
            }
        }

        state.selectionComplete = state.selectionComplete || !state.hearths.isEmpty();
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
        hearth.combatRoster.clear();
        assignments.forEach((entityId, role) -> {
            if (entityId != null && role != null && role != HearthEncounterRole.UNASSIGNED) {
                hearth.combatRoster.put(entityId, role);
            }
        });
        hearth.combatRosterInitialized = true;
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

    public boolean clearFirstTransmissionForDebug(UUID playerId, UUID hearthId) {
        HearthRecord hearth = hearth(hearthId).orElse(null);
        HearthContactMemory local = hearth == null ? null : hearth.playerContacts.get(playerId);
        if (local == null || (!local.firstTransmissionComplete
                && local.firstTransmissionGameTime < 0L)) {
            return false;
        }
        local.firstTransmissionComplete = false;
        local.firstTransmissionGameTime = -1L;
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
