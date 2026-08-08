package com.frozendawn.data;

import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthEncounterRole;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthPopulationRole;
import com.frozendawn.homo.HearthSelectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnedHearthSavedDataTest {

    @Test
    void selectionAndReservedFieldsRoundTripThroughNbt() {
        ReturnedHearthSavedData original = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(42, 71, -91);
        HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(123456L, anchor);

        assertTrue(original.rememberTransponderAnchor(anchor));
        assertTrue(original.applySelectionPlan(plan, 9876L));

        CompoundTag saved = original.save(new CompoundTag(), null);
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(saved, null);

        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
        assertEquals(anchor, loaded.transponderAnchor().orElseThrow());
        assertTrue(loaded.selectionComplete());
        assertEquals(9876L, loaded.selectionGameTime());
        assertEquals(original.legacyRelationship(), loaded.legacyRelationship());
        assertEquals(original.playerMemories(), loaded.playerMemories());
        assertEquals(original.hearths().size(), loaded.hearths().size());

        for (ReturnedHearthSavedData.HearthRecord expected : original.hearths()) {
            ReturnedHearthSavedData.HearthRecord actual = loaded.hearth(expected.type()).orElseThrow();
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.center(), actual.center());
            assertEquals(expected.layoutSeed(), actual.layoutSeed());
            assertEquals(expected.stage(), actual.stage());
            assertEquals(expected.mood(), actual.mood());
            assertEquals(expected.violationState(), actual.violationState());
            assertEquals(expected.lastPlayerContactGameTime(), actual.lastPlayerContactGameTime());
            assertEquals(expected.watcherSpawned(), actual.watcherSpawned());
            assertEquals(expected.watcherEntityId(), actual.watcherEntityId());
            assertEquals(expected.architectAssessorSpawned(), actual.architectAssessorSpawned());
            assertEquals(expected.architectAssessorEntityId(), actual.architectAssessorEntityId());
            assertEquals(expected.masterArchitectEntityId(), actual.masterArchitectEntityId());
            assertEquals(expected.masterArchitectDefeated(), actual.masterArchitectDefeated());
            assertEquals(expected.masterArchitectDefeatedGameTime(),
                    actual.masterArchitectDefeatedGameTime());
            assertEquals(expected.masterStormAftermathActive(),
                    actual.masterStormAftermathActive());
            assertEquals(expected.hearthStormDead(), actual.hearthStormDead());
            assertEquals(expected.decoherenceGranted(), actual.decoherenceGranted());
            assertEquals(expected.watchedStopWatchingGranted(),
                    actual.watchedStopWatchingGranted());
            assertEquals(expected.heartDestroyedNodeMask(),
                    actual.heartDestroyedNodeMask());
            assertEquals(expected.heartActiveNodeDamage(),
                    actual.heartActiveNodeDamage());
            assertEquals(expected.heartDebrisLandedMask(),
                    actual.heartDebrisLandedMask());
            assertEquals(expected.heartCollapseDebrisLanded(),
                    actual.heartCollapseDebrisLanded());
            assertEquals(expected.heartMusicActive(), actual.heartMusicActive());
        }
    }

    @Test
    void firstCompletedTransponderRemainsTheWorldAnchor() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos first = new BlockPos(1, 64, 2);
        BlockPos second = new BlockPos(300, 70, 400);

        assertTrue(state.rememberTransponderAnchor(first));
        assertFalse(state.rememberTransponderAnchor(second));
        assertEquals(first, state.transponderAnchor().orElseThrow());
    }

    @Test
    void applyingASelectionPlanIsIdempotent() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        HearthSelectionPolicy.SelectionPlan first = HearthSelectionPolicy.createPlan(
                1L, BlockPos.ZERO);
        HearthSelectionPolicy.SelectionPlan second = HearthSelectionPolicy.createPlan(
                2L, new BlockPos(1000, 64, 1000));

        assertTrue(state.applySelectionPlan(first, 10L));
        assertFalse(state.applySelectionPlan(second, 20L));
        assertEquals(first.major().id(), state.hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElseThrow().id());
        assertEquals(10L, state.selectionGameTime());
    }

    @Test
    void legacyBlankDataMigratesToTheCurrentSchema() {
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(new CompoundTag(), null);
        CompoundTag saved = loaded.save(new CompoundTag(), null);

        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, saved.getInt("dataVersion"));
        assertFalse(loaded.selectionComplete());
        assertTrue(loaded.hearths().isEmpty());
    }

    @Test
    void postMaeveMoonTimelinePersistsAndIgnoresRollback() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        long seed = 0x4D4F4F4E5F544553L;

        assertTrue(state.setMaeveErasedForDebug(true, 500L));
        assertTrue(state.schedulePostMaeveMoonrise(12_000L, 11_000L, seed));
        assertFalse(state.postMaeveMoonriseStarted());
        state.advancePostMaeveMoon(12_000L);
        state.advancePostMaeveMoon(12_025L);
        state.advancePostMaeveMoon(11_900L);

        assertTrue(state.postMaeveMoonriseStarted());
        assertEquals(25L, state.postMaeveMoonElapsedDayTicks());
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        assertEquals(12_000L, loaded.postMaeveMoonriseStartDayTime());
        assertEquals(25L, loaded.postMaeveMoonElapsedDayTicks());
        assertEquals(11_900L, loaded.postMaeveMoonLastDayTime());
        assertEquals(seed, loaded.postMaeveMoonVisualSeed());
        assertTrue(loaded.postMaeveMoonriseStarted());
    }

    @Test
    void legacyPostMaeveSaveWaitsForANewMoonriseSchedule() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("dataVersion", ReturnedHearthSavedData.CURRENT_DATA_VERSION - 1);
        legacy.putBoolean("maeveErased", true);
        legacy.putLong("maeveErasedGameTime", 4_000L);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(legacy, null);

        assertTrue(loaded.maeveErased());
        assertEquals(-1L, loaded.postMaeveMoonriseStartDayTime());
        assertEquals(-1L, loaded.postMaeveMoonElapsedDayTicks());
        assertFalse(loaded.postMaeveMoonriseStarted());
    }

    @Test
    void phaseGatingDoesNotBankIneligibleWorldTime() {
        ReturnedHearthSavedData state = selectedState(1000L);

        state.updateMaturation(25_000L, false);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertEquals(0L, major.maturityTicks());
        assertEquals(25_000L, major.lastUpdatedGameTime());

        state.updateMaturation(49_000L, true);
        assertEquals(HearthMaturationPolicy.MINECRAFT_DAY_TICKS, major.maturityTicks());
        assertEquals(ReturnedHearthSavedData.HearthStage.TRACE, major.stage());
    }

    @Test
    void duplicateTicksDoNotDoubleAdvanceMaturity() {
        ReturnedHearthSavedData state = selectedState(1000L);

        state.updateMaturation(2000L, true);
        state.updateMaturation(2000L, true);

        assertEquals(1000L, major(state).maturityTicks());
    }

    @Test
    void timeRollbackResetsTheBaselineWithoutRemovingMaturity() {
        ReturnedHearthSavedData state = selectedState(1000L);

        state.updateMaturation(2000L, true);
        state.updateMaturation(1500L, true);
        state.updateMaturation(1600L, true);

        assertEquals(1100L, major(state).maturityTicks());
        assertEquals(1600L, major(state).lastUpdatedGameTime());
    }

    @Test
    void loadedRecordsCatchUpFromTheirPersistedTimestamp() {
        ReturnedHearthSavedData original = selectedState(1000L);
        CompoundTag saved = original.save(new CompoundTag(), null);
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(saved, null);

        ReturnedHearthSavedData.MaturationResult result = loaded.updateMaturation(
                1000L + HearthMaturationPolicy.FORMED_START_TICKS, true);

        assertEquals(loaded.hearths().size(), result.transitions().size());
        assertEquals(HearthMaturationPolicy.FORMED_START_TICKS, major(loaded).maturityTicks());
        assertEquals(ReturnedHearthSavedData.HearthStage.FORMED, major(loaded).stage());
    }

    @Test
    void debugAdvanceUsesTheSameStagePolicy() {
        ReturnedHearthSavedData state = selectedState(1000L);

        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 1000L);

        assertEquals(ReturnedHearthSavedData.HearthStage.INTACT, major(state).stage());
        state.hearth(HearthSelectionPolicy.HearthType.MINOR).ifPresent(minor ->
                assertEquals(ReturnedHearthSavedData.HearthStage.FORMED, minor.stage()));
    }

    @Test
    void traceReconciliationProgressIsMonotonicAndPersists() {
        ReturnedHearthSavedData state = selectedState(1000L);
        state.advanceMaturationForDebug(HearthMaturationPolicy.TRACE_START_TICKS, 1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        BlockPos resolved = new BlockPos(major.center().getX() + 8, 71, major.center().getZ() - 8);

        assertTrue(state.resolveSurface(major.id(), resolved));
        assertFalse(state.resolveSurface(major.id(), resolved.above()));
        assertTrue(state.recordStructureProgress(major.id(), 1, 12,
                ReturnedHearthSavedData.HearthStage.PLANNED, false));
        assertFalse(state.recordStructureProgress(major.id(), 1, 6,
                ReturnedHearthSavedData.HearthStage.PLANNED, false));

        CompoundTag saved = state.save(new CompoundTag(), null);
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(saved, null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(resolved, restored.center());
        assertTrue(restored.surfaceResolved());
        assertEquals(1, restored.structurePlanVersion());
        assertEquals(12, restored.structureCursor());
        assertFalse(restored.structurePlaced());

        assertTrue(loaded.recordStructureProgress(restored.id(), 1, 48,
                ReturnedHearthSavedData.HearthStage.TRACE, true));
        assertTrue(restored.structurePlaced());
        assertEquals(ReturnedHearthSavedData.HearthStage.TRACE, restored.structureStageApplied());
        assertFalse(loaded.recordStructureProgress(restored.id(), 1, 48,
                ReturnedHearthSavedData.HearthStage.TRACE, true));
    }

    @Test
    void newerLayoutVersionReopensCompletedReconciliation() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);

        state.recordStructureProgress(major.id(), 1, 20,
                ReturnedHearthSavedData.HearthStage.TRACE, true);
        assertTrue(major.structurePlaced());

        assertTrue(state.recordStructureProgress(major.id(), 2, 0,
                ReturnedHearthSavedData.HearthStage.PLANNED, false));
        assertFalse(major.structurePlaced());
        assertEquals(2, major.structurePlanVersion());
        assertEquals(0, major.structureCursor());
    }

    @Test
    void firstWatcherBindingIsPersistentAndCannotBeReplaced() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        java.util.UUID first = java.util.UUID.randomUUID();
        java.util.UUID second = java.util.UUID.randomUUID();

        assertTrue(state.bindWatcher(major.id(), first, "returned_watcher"));
        assertFalse(state.bindWatcher(major.id(), second, "hunter_watcher"));
        assertTrue(major.watcherSpawned());
        assertEquals(first, major.watcherEntityId().orElseThrow());
        assertEquals("returned_watcher", major.boundVariantProfile());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertTrue(restored.watcherSpawned());
        assertEquals(first, restored.watcherEntityId().orElseThrow());
        assertEquals("returned_watcher", restored.boundVariantProfile());
    }

    @Test
    void debugWatcherResetAllowsADeadWatcherToBeReconciledAgain() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID first = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(state.bindWatcher(major.id(), first, "returned_watcher"));
        assertTrue(state.clearWatcherBindingForDebug(major.id()));
        assertFalse(major.watcherSpawned());
        assertTrue(major.watcherEntityId().isEmpty());
        assertTrue(major.boundVariantProfile().isBlank());
        assertTrue(state.bindWatcher(major.id(), replacement, "returned_watcher"));
        assertEquals(replacement, major.watcherEntityId().orElseThrow());
    }

    @Test
    void architectAssessorBindingIsPersistentAndDebugResettable() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID first = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(state.bindArchitectAssessor(major.id(), first, "architect_assessor"));
        assertFalse(state.bindArchitectAssessor(major.id(), replacement, "architect_assessor"));
        assertTrue(major.architectAssessorSpawned());
        assertEquals(first, major.architectAssessorEntityId().orElseThrow());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(first, restored.architectAssessorEntityId().orElseThrow());
        assertEquals("architect_assessor", restored.architectAssessorProfile());
        assertTrue(loaded.clearArchitectAssessorBindingForDebug(restored.id()));
        assertTrue(loaded.bindArchitectAssessor(restored.id(), replacement, "architect_assessor"));
    }

    @Test
    void intactPopulationBindingsRoundTripByRole() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);

        for (HearthPopulationRole role : HearthPopulationRole.values()) {
            assertTrue(state.bindPopulationResident(major.id(), role, UUID.randomUUID()));
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(HearthPopulationRole.values().length,
                restored.populationResidents().size());
        for (HearthPopulationRole role : HearthPopulationRole.values()) {
            ReturnedHearthSavedData.HearthResidentBinding expected =
                    major.populationResident(role).orElseThrow();
            ReturnedHearthSavedData.HearthResidentBinding actual =
                    restored.populationResident(role).orElseThrow();
            assertEquals(expected.entityId(), actual.entityId());
            assertEquals(-1L, actual.respawnAfterGameTime());
        }
    }

    @Test
    void residentDeathSchedulesPersistentDelayedReplacement() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID resident = UUID.randomUUID();
        UUID wrong = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(state.bindPopulationResident(
                major.id(), HearthPopulationRole.MIMIC, resident));
        assertFalse(state.bindPopulationResident(
                major.id(), HearthPopulationRole.MIMIC, replacement));
        assertFalse(state.markPopulationResidentMissing(
                major.id(), HearthPopulationRole.MIMIC, wrong, 5000L));
        assertTrue(state.markPopulationResidentMissing(
                major.id(), HearthPopulationRole.MIMIC, resident, 5000L));

        ReturnedHearthSavedData.HearthResidentBinding missing = major
                .populationResident(HearthPopulationRole.MIMIC).orElseThrow();
        assertTrue(missing.entityId().isEmpty());
        assertEquals(5000L + HearthPopulationPolicy.RESPAWN_DELAY_TICKS,
                missing.respawnAfterGameTime());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthResidentBinding restored = major(loaded)
                .populationResident(HearthPopulationRole.MIMIC).orElseThrow();
        assertTrue(restored.entityId().isEmpty());
        assertEquals(missing.respawnAfterGameTime(), restored.respawnAfterGameTime());
        assertTrue(loaded.bindPopulationResident(
                major(loaded).id(), HearthPopulationRole.MIMIC, replacement));
        assertEquals(replacement, major(loaded).populationResident(HearthPopulationRole.MIMIC)
                .orElseThrow().entityId().orElseThrow());
    }

    @Test
    void playerCausedPassiveDeathLeavesAPermanentPopulationVacancy() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID resident = UUID.randomUUID();

        assertTrue(state.bindPopulationResident(
                major.id(), HearthPopulationRole.RETURNED, resident));
        assertTrue(state.markPopulationResidentMissing(
                major.id(), HearthPopulationRole.RETURNED, resident, 5000L, true));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthResidentBinding vacancy = major(loaded)
                .populationResident(HearthPopulationRole.RETURNED).orElseThrow();
        assertTrue(vacancy.permanentlyVacant());
        assertTrue(vacancy.entityId().isEmpty());
        assertEquals(-1L, vacancy.respawnAfterGameTime());
        assertFalse(loaded.bindPopulationResident(
                major(loaded).id(), HearthPopulationRole.RETURNED, UUID.randomUUID()));
    }

    @Test
    void combatRosterRolesPersistAndTetherReleasePreservesSpentResidents() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID dispatched = UUID.randomUUID();
        UUID tethered = UUID.randomUUID();
        UUID spent = UUID.randomUUID();

        assertTrue(state.initializeCombatRoster(major.id(), Map.of(
                dispatched, HearthEncounterRole.DISPATCHED,
                tethered, HearthEncounterRole.TETHERED,
                spent, HearthEncounterRole.SPENT)));
        assertFalse(state.initializeCombatRoster(major.id(), Map.of()));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertTrue(restored.combatRosterInitialized());
        assertEquals(HearthEncounterRole.DISPATCHED,
                loaded.encounterRole(restored.id(), dispatched));
        assertEquals(HearthEncounterRole.TETHERED,
                loaded.encounterRole(restored.id(), tethered));
        assertEquals(HearthEncounterRole.SPENT,
                loaded.encounterRole(restored.id(), spent));

        assertEquals(1, loaded.releaseEncounterTethers(restored.id()));
        assertEquals(HearthEncounterRole.RESERVED,
                loaded.encounterRole(restored.id(), tethered));
        assertEquals(HearthEncounterRole.SPENT,
                loaded.encounterRole(restored.id(), spent));
    }

    @Test
    void legacyUntetheredBystandersMigrateToActiveReserves() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID legacyBystander = UUID.randomUUID();

        assertTrue(state.initializeCombatRoster(major.id(), Map.of(
                legacyBystander, HearthEncounterRole.BYSTANDER)));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        assertEquals(HearthEncounterRole.RESERVED,
                loaded.encounterRole(major(loaded).id(), legacyBystander));
    }

    @Test
    void congregationCasualtyLedgerIsPermanentAndDeduplicated() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        UUID casualty = UUID.randomUUID();

        assertTrue(state.recordCongregationCasualty(
                player, major.id(), casualty, 8000L));
        assertFalse(state.recordCongregationCasualty(
                player, major.id(), casualty, 9000L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.PlayerHiveMemory memory = loaded.playerMemory(player)
                .orElseThrow();
        assertEquals(1, memory.congregationCasualties());
        assertEquals(8000L, memory.lastCongregationCasualtyGameTime());
        assertEquals(major.id(), memory.lastCongregationCasualtyHearthId().orElseThrow());

        loaded.clearPlayerViolationsForDebug(player);
        assertEquals(1, loaded.playerMemory(player).orElseThrow()
                .congregationCasualties());
    }

    @Test
    void masterArchitectBindingPersistsAndDefeatIsPermanent() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID first = UUID.randomUUID();
        UUID wrong = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();

        assertTrue(state.bindMasterArchitect(major.id(), first));
        assertFalse(state.bindMasterArchitect(major.id(), replacement));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(first, restored.masterArchitectEntityId().orElseThrow());
        assertFalse(restored.masterArchitectDefeated());
        assertEquals(-1L, restored.masterArchitectDefeatedGameTime());

        assertFalse(loaded.markMasterArchitectDefeated(
                restored.id(), wrong, 5000L));
        assertTrue(loaded.markMasterArchitectDefeated(
                restored.id(), first, 5000L));
        assertTrue(restored.masterArchitectEntityId().isEmpty());
        assertTrue(restored.masterArchitectDefeated());
        assertEquals(5000L, restored.masterArchitectDefeatedGameTime());
        UUID killer = UUID.randomUUID();
        assertTrue(loaded.markDecoherenceGranted(restored.id()));
        assertTrue(loaded.beginMasterArchitectStormAftermath(
                restored.id(), 5070L, 0.75F, killer));

        ReturnedHearthSavedData aftermathReload = ReturnedHearthSavedData.load(
                loaded.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord active = major(aftermathReload);
        assertTrue(active.masterStormAftermathActive());
        assertEquals(5070L, active.masterStormAftermathStartGameTime());
        assertEquals(0.75F, active.masterStormAftermathStrength());
        assertEquals(killer, active.masterStormAftermathKillerId().orElseThrow());
        assertFalse(active.hearthStormDead());
        assertTrue(active.decoherenceGranted());
        assertFalse(active.watchedStopWatchingGranted());
        assertTrue(aftermathReload.completeMasterArchitectStormAftermath(active.id()));
        assertTrue(active.hearthStormDead());
        assertFalse(active.masterStormAftermathActive());
        assertTrue(aftermathReload.markWatchedStopWatchingGranted(active.id()));
        assertFalse(aftermathReload.markWatchedStopWatchingGranted(active.id()));
        assertFalse(loaded.bindMasterArchitect(restored.id(), replacement));
        assertTrue(loaded.initializeCombatRoster(restored.id(), Map.of(
                UUID.randomUUID(), HearthEncounterRole.DISPATCHED)));

        ReturnedHearthSavedData defeatedReload = ReturnedHearthSavedData.load(
                loaded.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord defeated = major(defeatedReload);
        assertTrue(defeated.masterArchitectDefeated());
        assertEquals(5000L, defeated.masterArchitectDefeatedGameTime());
        assertTrue(defeatedReload.resetMasterArchitectForDebug(defeated.id()));
        assertFalse(defeated.decoherenceGranted());
        assertFalse(defeated.watchedStopWatchingGranted());
        assertFalse(defeated.combatRosterInitialized());
        assertTrue(defeated.combatRoster().isEmpty());
        assertTrue(defeatedReload.bindMasterArchitect(defeated.id(), replacement));
        assertEquals(replacement, defeated.masterArchitectEntityId().orElseThrow());
    }

    @Test
    void heartFormationAuthorityRoundTripsAndResetDoesNotResurrectMaster() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID master = UUID.randomUUID();
        UUID killer = UUID.randomUUID();
        UUID heart = UUID.randomUUID();
        BlockPos deathAnchor = major.center().offset(9, 3, -6);
        List<ReturnedHearthSavedData.HeartFragmentSnapshot> fragments =
                java.util.stream.IntStream.range(0, 45)
                        .mapToObj(index -> new ReturnedHearthSavedData.HeartFragmentSnapshot(
                                new BlockPos(index - 20, index % 4, 20 - index),
                                index % 3 == 0 ? "minecraft:ice" : "minecraft:packed_ice"))
                        .toList();

        assertTrue(state.bindMasterArchitect(major.id(), master));
        assertTrue(state.prepareHeartFormation(major.id(), deathAnchor, fragments));
        assertTrue(state.markMasterArchitectDefeated(major.id(), master, 5000L));
        assertTrue(state.beginMasterArchitectStormAftermath(
                major.id(), 5070L, 0.625F, killer));
        assertTrue(state.completeMasterArchitectStormAftermath(major.id()));
        assertTrue(state.markWatchedStopWatchingGranted(major.id()));
        assertTrue(state.startHeartFormation(major.id(), 5600L));
        assertTrue(major.heartMusicActive());
        assertTrue(state.markHeartAdvancementFired(major.id()));
        assertTrue(state.bindHeartEntity(major.id(), heart));
        assertTrue(state.markHeartLive(major.id()));
        assertTrue(state.markHeartConvergenceStarted(major.id()));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(deathAnchor, restored.heartAnchor().orElseThrow());
        assertEquals(major.heartLayoutSeed(), restored.heartLayoutSeed());
        assertEquals(0.625F, restored.heartFieldStrength());
        assertEquals(5600L, restored.heartFormationStartGameTime());
        assertTrue(restored.heartAdvancementFired());
        assertTrue(restored.heartLive());
        assertTrue(restored.heartConvergenceStarted());
        assertTrue(restored.heartMusicActive());
        assertEquals(heart, restored.heartEntityId().orElseThrow());
        assertEquals(40, restored.heartFragments().size());
        assertEquals(fragments.getFirst(), restored.heartFragments().getFirst());

        assertTrue(loaded.resetHeartForDebug(restored.id()));
        assertTrue(restored.masterArchitectDefeated());
        assertTrue(restored.heartAnchor().isEmpty());
        assertEquals(-1L, restored.heartFormationStartGameTime());
        assertTrue(restored.heartFormationSuppressed());
        assertTrue(restored.heartEntityId().isEmpty());
        assertTrue(restored.heartFragments().isEmpty());
        assertFalse(restored.heartMusicActive());
        assertTrue(loaded.startHeartFormation(restored.id(), 7000L));
        assertFalse(restored.heartFormationSuppressed());
        assertTrue(restored.heartMusicActive());
        assertTrue(loaded.stopHeartMusic(restored.id()));
        assertFalse(restored.heartMusicActive());
    }

    @Test
    void heartMemoryNodeDamagePersistsAndEnforcesOrder() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));

        assertTrue(state.damageHeartMemoryNode(major.id(), 0).accepted());
        ReturnedHearthSavedData.HeartNodeDamageResult second =
                state.damageHeartMemoryNode(major.id(), 0);
        assertTrue(second.accepted());
        assertFalse(second.destroyed());
        assertEquals(2, second.activeDamage());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(0, restored.heartDestroyedNodeMask());
        assertEquals(2, restored.heartActiveNodeDamage());
        assertFalse(loaded.damageHeartMemoryNode(restored.id(), 1).accepted());

        ReturnedHearthSavedData.HeartNodeDamageResult destroyed =
                loaded.damageHeartMemoryNode(restored.id(), 0);
        assertTrue(destroyed.destroyed());
        assertEquals(0b00001, destroyed.destroyedMask());
        assertEquals(0, destroyed.activeDamage());
        assertTrue(loaded.damageHeartMemoryNode(restored.id(), 1).accepted());
    }

    @Test
    void heartScavengerAndSuccessorAuthorityRoundTripsAndResets() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID successor = UUID.randomUUID();
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        assertTrue(state.markHeartSwarmAnnounced(major.id()));
        assertFalse(state.markHeartSwarmAnnounced(major.id()));
        assertTrue(state.scheduleHeartScavengerWave(major.id(), 5400L));
        assertTrue(state.bindHeartSuccessor(major.id(), successor, 1, 5500L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertTrue(restored.heartSwarmAnnounced());
        assertEquals(5400L, restored.heartScavengerNextWaveGameTime());
        assertEquals(successor, restored.heartSuccessorEntityId().orElseThrow());
        assertEquals(1, restored.heartSuccessorGeneration());
        assertEquals(5500L, restored.heartSuccessorRespawnGameTime());

        assertTrue(loaded.scheduleHeartSuccessorRespawn(
                restored.id(), 2, 6100L));
        assertTrue(restored.heartSuccessorEntityId().isEmpty());
        assertEquals(2, restored.heartSuccessorGeneration());
        assertEquals(6100L, restored.heartSuccessorRespawnGameTime());
        assertTrue(loaded.resetHeartMemoryNodesForDebug(restored.id()));
        assertFalse(restored.heartSwarmAnnounced());
        assertEquals(-1L, restored.heartScavengerNextWaveGameTime());
        assertTrue(restored.heartSuccessorEntityId().isEmpty());
        assertEquals(-1L, restored.heartSuccessorRespawnGameTime());
        assertEquals(0, restored.heartSuccessorGeneration());
    }

    @Test
    void heartDebrisLifecyclePersistsWithoutRecreatingLandedPieces() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));

        for (int hit = 0; hit < 3; hit++) {
            state.damageHeartMemoryNode(major.id(), 0, 5432L);
        }
        assertEquals(5432L, major.heartNodeDestroyedGameTime(0));
        assertEquals(0, major.heartDebrisLandedMask());
        assertTrue(state.markHeartNodeDebrisLanded(major.id(), 0));
        assertFalse(state.markHeartNodeDebrisLanded(major.id(), 0));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(5432L, restored.heartNodeDestroyedGameTime(0));
        assertEquals(0b00001, restored.heartDebrisLandedMask());
        assertFalse(loaded.markHeartCollapseDebrisLanded(restored.id()));

        for (int node = 1;
             node < com.frozendawn.homo.HeartLattice.NODE_COUNT;
             node++) {
            for (int hit = 0;
                 hit < com.frozendawn.homo.HeartLattice.HITS_PER_NODE;
                 hit++) {
                assertTrue(loaded.damageHeartMemoryNode(
                        restored.id(), node, 5432L + node).accepted());
            }
        }
        assertTrue(loaded.startHeartCollapse(restored.id(), 6200L));
        assertTrue(loaded.completeHeartCollapse(restored.id()));
        assertTrue(loaded.markHeartCollapseDebrisLanded(restored.id()));
        assertFalse(loaded.markHeartCollapseDebrisLanded(restored.id()));

        ReturnedHearthSavedData collapsed = ReturnedHearthSavedData.load(
                loaded.save(new CompoundTag(), null), null);
        assertTrue(major(collapsed).heartCollapseDebrisLanded());
    }

    @Test
    void heartCollapsePersistsAndExposesMaeveWithoutStoppingMusic() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        destroyAllHeartNodes(state, major.id());

        assertTrue(state.startHeartCollapse(major.id(), 6200L));
        assertFalse(state.startHeartCollapse(major.id(), 6300L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(6200L, restored.heartCollapseStartGameTime());
        assertFalse(restored.heartCollapseComplete());
        assertFalse(restored.heartMaeveExposed());
        assertFalse(restored.heartLive());

        assertTrue(loaded.completeHeartCollapse(restored.id()));
        assertFalse(restored.heartLive());
        assertTrue(restored.heartCollapseComplete());
        assertTrue(restored.heartMaeveExposed());
        assertTrue(restored.heartMusicActive());
    }

    @Test
    void maeveErasureIsDeliberatePersistentAndFinal() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID eraser = UUID.randomUUID();
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        assertFalse(state.startHeartMaeveErasure(major.id(), 6100L, eraser));
        destroyAllHeartNodes(state, major.id());
        assertTrue(state.startHeartCollapse(major.id(), 6200L));
        assertTrue(state.completeHeartCollapse(major.id()));

        assertTrue(state.startHeartMaeveErasure(major.id(), 7000L, eraser));
        assertFalse(state.startHeartMaeveErasure(major.id(), 7001L, eraser));
        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(7000L, restored.heartMaeveErasureStartGameTime());
        assertEquals(eraser, restored.heartMaeveEraserId().orElseThrow());
        assertFalse(restored.heartMaeveErasureComplete());
        assertTrue(restored.heartMaeveExposed());
        assertTrue(restored.heartMusicActive());

        assertTrue(loaded.completeHeartMaeveErasure(restored.id()));
        assertTrue(restored.heartMaeveErasureComplete());
        assertFalse(restored.heartMaeveExposed());
        assertFalse(restored.heartMusicActive());
        assertTrue(restored.heartEntityId().isEmpty());
        assertTrue(loaded.markHeartFinalAdvancementGranted(restored.id()));
        assertFalse(loaded.markHeartFinalAdvancementGranted(restored.id()));

        assertTrue(loaded.resetHeartMaeveErasureForDebug(restored.id()));
        assertEquals(-1L, restored.heartMaeveErasureStartGameTime());
        assertFalse(restored.heartMaeveErasureComplete());
        assertTrue(restored.heartMaeveExposed());
        assertFalse(restored.heartFinalAdvancementGranted());
        assertTrue(restored.heartMusicActive());
    }

    @Test
    void postMaeveAuthorityIsIrreversibleAndPersistsReleaseState() {
        ReturnedHearthSavedData state = selectedState(1000L);

        assertTrue(state.markMaeveErased(7200L));
        assertFalse(state.markMaeveErased(7300L));
        assertTrue(state.markUndoneSpawningReleased());
        assertFalse(state.markUndoneSpawningReleased());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        assertTrue(loaded.maeveErased());
        assertEquals(7200L, loaded.maeveErasedGameTime());
        assertTrue(loaded.undoneSpawningReleased());
    }

    @Test
    void biologicalWarningTimePersistsForBloomReleaseClock() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);

        assertTrue(state.markHeartMaeveBiologicalWarningPlayed(
                major.id(), 8_088L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertTrue(restored.heartMaeveBiologicalWarningPlayed());
        assertEquals(8_088L, restored.heartMaeveBiologicalWarningGameTime());
    }

    @Test
    void debugBiologicalWarningReplayReplacesTheReleaseClock() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);

        assertTrue(state.markHeartMaeveBiologicalWarningPlayed(
                major.id(), 8_088L));
        assertTrue(state.replayHeartMaeveBiologicalWarningForDebug(
                major.id(), 19_000L));
        assertEquals(19_000L, major.heartMaeveBiologicalWarningGameTime());
    }

    @Test
    void legacyStartedMaeveErasureMigratesToPostMaeveAuthority() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        destroyAllHeartNodes(state, major.id());
        assertTrue(state.startHeartCollapse(major.id(), 6200L));
        assertTrue(state.completeHeartCollapse(major.id()));
        assertTrue(state.startHeartMaeveErasure(major.id(), 7000L, UUID.randomUUID()));

        CompoundTag legacy = state.save(new CompoundTag(), null);
        legacy.putInt("dataVersion", 22);
        legacy.remove("maeveErased");
        legacy.remove("maeveErasedGameTime");
        legacy.remove("undoneSpawningReleased");

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(legacy, null);
        assertTrue(loaded.maeveErased());
        assertEquals(7000L, loaded.maeveErasedGameTime());
        assertFalse(loaded.undoneSpawningReleased());
    }

    @Test
    void legacyHeartOnlySaveDoesNotMigrateToMaeveErased() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));

        CompoundTag legacy = state.save(new CompoundTag(), null);
        legacy.putInt("dataVersion", 22);
        legacy.remove("maeveErased");
        legacy.remove("maeveErasedGameTime");
        legacy.remove("undoneSpawningReleased");

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(legacy, null);
        assertFalse(loaded.maeveErased());
        assertEquals(-1L, loaded.maeveErasedGameTime());
        assertFalse(loaded.undoneSpawningReleased());
    }

    @Test
    void resettingHeartNodesAlsoResetsCollapseForAnotherSmokePass() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        destroyAllHeartNodes(state, major.id());
        assertTrue(state.startHeartCollapse(major.id(), 6200L));
        assertTrue(state.completeHeartCollapse(major.id()));

        assertTrue(state.resetHeartMemoryNodesForDebug(major.id()));
        assertEquals(0, major.heartDestroyedNodeMask());
        assertEquals(-1L, major.heartCollapseStartGameTime());
        assertFalse(major.heartCollapseComplete());
        assertFalse(major.heartMaeveExposed());
        assertTrue(major.heartLive());
        assertEquals(0, major.heartDebrisLandedMask());
        assertFalse(major.heartCollapseDebrisLanded());
    }

    @Test
    void versionSixteenCompletedArchiveMigratesToDormantRemnant() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertTrue(state.startHeartFormation(major.id(), 5000L));
        assertTrue(state.markHeartLive(major.id()));
        destroyAllHeartNodes(state, major.id());
        CompoundTag versionSixteen = state.save(new CompoundTag(), null);
        versionSixteen.putInt("dataVersion", 16);
        for (Tag entry : versionSixteen.getList("hearths", Tag.TAG_COMPOUND)) {
            CompoundTag hearth = (CompoundTag) entry;
            hearth.remove("heartCollapseStartGameTime");
            hearth.remove("heartCollapseComplete");
            hearth.remove("heartMaeveExposed");
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                versionSixteen, null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertTrue(restored.heartCollapseComplete());
        assertTrue(restored.heartMaeveExposed());
        assertFalse(restored.heartLive());
    }

    @Test
    void fifthMemoryCanErasePlayerKnowledgeWithoutErasingTheHearth() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        UUID resident = UUID.randomUUID();

        state.recordPlayerContact(player, major.id(), 2000L);
        state.recordHearthViolation(player, major.id(), 2100L,
                ReturnedHearthSavedData.HearthViolationReason.ENTITY_ATTACK);
        assertTrue(state.recordCongregationCasualty(
                player, major.id(), resident, 2200L));
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                state.relationship(player));

        int hearthCount = state.hearths().size();
        assertTrue(state.erasePlayerFromHive(player));
        assertEquals(hearthCount, state.hearths().size());
        assertTrue(state.playerMemory(player).isEmpty());
        assertTrue(major.playerContact(player).isEmpty());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                state.relationship(player));
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.HearthDisposition.DORMANT,
                    hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.NONE,
                    hearth.violationState());
        }
    }

    @Test
    void versionEightDataMigratesWithoutInventingAMasterArchitect() {
        ReturnedHearthSavedData state = selectedState(1000L);
        CompoundTag versionEight = state.save(new CompoundTag(), null);
        versionEight.putInt("dataVersion", 8);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionEight, null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);

        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
        assertTrue(restored.masterArchitectEntityId().isEmpty());
        assertFalse(restored.masterArchitectDefeated());
        assertEquals(-1L, restored.masterArchitectDefeatedGameTime());
    }

    @Test
    void completedStormMigrationDoesNotInventASecondAdvancementToast() {
        ReturnedHearthSavedData state = selectedState(1000L);
        CompoundTag versionTwelve = state.save(new CompoundTag(), null);
        versionTwelve.putInt("dataVersion", 12);
        for (Tag entry : versionTwelve.getList("hearths", Tag.TAG_COMPOUND)) {
            CompoundTag hearth = (CompoundTag) entry;
            if ("MAJOR".equals(hearth.getString("type"))) {
                hearth.putBoolean("hearthStormDead", true);
                hearth.putBoolean("decoherenceGranted", true);
                hearth.remove("watchedStopWatchingGranted");
            }
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionTwelve, null);

        assertTrue(major(loaded).decoherenceGranted());
        assertTrue(major(loaded).watchedStopWatchingGranted());
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
    }

    @Test
    void versionSevenDataMigratesWithAnEmptyPopulation() {
        ReturnedHearthSavedData state = selectedState(1000L);
        CompoundTag versionSeven = state.save(new CompoundTag(), null);
        versionSeven.putInt("dataVersion", 7);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionSeven, null);

        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
        assertTrue(major(loaded).populationResidents().isEmpty());
    }

    @Test
    void firstContactCreatesGlobalAndHearthLocalMemory() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        ReturnedHearthSavedData.ContactResult first = state.recordPlayerContact(
                player, major.id(), 2000L);
        ReturnedHearthSavedData.ContactResult duplicate = state.recordPlayerContact(
                player, major.id(), 2020L);

        assertTrue(first.changed());
        assertTrue(first.firstGlobalContact());
        assertTrue(first.firstHearthContact());
        assertTrue(first.newVisit());
        assertFalse(duplicate.changed());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                state.relationship(player));
        assertEquals(ReturnedHearthSavedData.HearthDisposition.WATCHFUL, major.mood());
        assertEquals(1, state.playerMemory(player).orElseThrow().totalVisits());
        ReturnedHearthSavedData.HearthContactMemory local = major.playerContact(player)
                .orElseThrow();
        assertEquals(1, local.visits());
        assertFalse(local.attackedWatcher());
    }

    @Test
    void aPlayerReturningAfterAnAbsenceCreatesAnotherVisit() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        state.recordPlayerContact(player, major.id(), 2000L);
        state.recordPlayerContact(player, major.id(),
                2000L + ReturnedHearthSavedData.CONTACT_SAVE_INTERVAL_TICKS);
        ReturnedHearthSavedData.ContactResult returned = state.recordPlayerContact(
                player, major.id(),
                2000L + ReturnedHearthSavedData.CONTACT_SAVE_INTERVAL_TICKS
                        + ReturnedHearthSavedData.NEW_VISIT_GAP_TICKS);

        assertTrue(returned.newVisit());
        assertEquals(2, state.playerMemory(player).orElseThrow().totalVisits());
        assertEquals(2, major.playerContact(player).orElseThrow().visits());
    }

    @Test
    void suspicionIsGlobalButDoesNotMakeWatchersHostile() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        UUID player = UUID.randomUUID();
        ReturnedHearthSavedData.HearthRecord major = major(state);

        assertTrue(state.markPlayerSuspicious(player, major.id(), 2000L));
        assertEquals(2, state.hearths().size());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS,
                state.relationship(player));
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.HearthDisposition.AGITATED, hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.SUSPICIOUS,
                    hearth.violationState());
        }
    }

    @Test
    void attackingAWatcherPermanentlyViolatesEveryHearth() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        UUID player = UUID.randomUUID();
        ReturnedHearthSavedData.HearthRecord major = major(state);

        assertTrue(state.markPlayerOrsathae(player, major.id(), 2000L));
        assertFalse(state.markPlayerSuspicious(player, major.id(), 3000L));
        assertEquals(2, state.hearths().size());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                state.relationship(player));
        assertTrue(major.playerContact(player).orElseThrow().attackedWatcher());
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.HearthDisposition.HOSTILE, hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.VIOLATED,
                    hearth.violationState());
        }
    }

    @Test
    void hiveAndLocalMemorySurviveLogoutDeathAndReloadBoundaries() {
        ReturnedHearthSavedData state = selectedState(1000L);
        UUID player = UUID.randomUUID();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        state.markPlayerOrsathae(player, major.id(), 2000L);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);

        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                loaded.relationship(player));
        ReturnedHearthSavedData.PlayerHiveMemory global = loaded.playerMemory(player)
                .orElseThrow();
        assertEquals(2000L, global.firstContactGameTime());
        assertEquals(major.id(), global.relationshipSourceHearthId().orElseThrow());
        ReturnedHearthSavedData.HearthContactMemory local = major(loaded)
                .playerContact(player).orElseThrow();
        assertTrue(local.attackedWatcher());
        assertEquals(1, local.visits());
    }

    @Test
    void versionThreeGlobalHostilityMigratesWithoutLosingItsMeaning() {
        CompoundTag legacy = selectedState(1000L).save(new CompoundTag(), null);
        legacy.putInt("dataVersion", 3);
        legacy.remove("legacyRelationship");
        legacy.remove("playerMemories");
        legacy.putString("globalDisposition", "HOSTILE");
        legacy.putBoolean("permanentOrsathae", true);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(legacy, null);

        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                loaded.legacyRelationship());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                loaded.relationship(UUID.randomUUID()));
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
    }

    @Test
    void versionFourHiveMemoryMigratesWithEmptyArchitectState() {
        CompoundTag versionFour = selectedState(1000L).save(new CompoundTag(), null);
        versionFour.putInt("dataVersion", 4);
        ListTag hearths = versionFour.getList("hearths", Tag.TAG_COMPOUND);
        for (Tag entry : hearths) {
            CompoundTag hearth = (CompoundTag) entry;
            hearth.remove("architectAssessorSpawned");
            hearth.remove("architectAssessorEntityId");
            hearth.remove("architectAssessorProfile");
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionFour, null);

        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
        for (ReturnedHearthSavedData.HearthRecord hearth : loaded.hearths()) {
            assertFalse(hearth.architectAssessorSpawned());
            assertTrue(hearth.architectAssessorEntityId().isEmpty());
            assertTrue(hearth.architectAssessorProfile().isBlank());
        }
    }

    @Test
    void debugRelationshipOverrideCanResetPermanentStateForTesting() {
        ReturnedHearthSavedData state = selectedState(1000L);
        UUID player = UUID.randomUUID();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        state.markPlayerOrsathae(player, major.id(), 2000L);

        assertTrue(state.setRelationshipForDebug(
                player, ReturnedHearthSavedData.HiveRelationship.NEUTRAL, 3000L));

        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                state.relationship(player));
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            ReturnedHearthSavedData.HearthDisposition expected = hearth.playerContacts().isEmpty()
                    ? ReturnedHearthSavedData.HearthDisposition.DORMANT
                    : ReturnedHearthSavedData.HearthDisposition.WATCHFUL;
            assertEquals(expected, hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.NONE,
                    hearth.violationState());
        }
    }

    @Test
    void neutralArchitectAssessmentRecordsContactWithoutEscalation() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        ReturnedHearthSavedData.AssessmentResult result = state.recordArchitectAssessment(
                player, major.id(), 2000L, false);

        assertTrue(result.completedNow());
        assertFalse(result.orsaDetected());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                state.relationship(player));
        ReturnedHearthSavedData.HearthContactMemory local = major.playerContact(player)
                .orElseThrow();
        assertTrue(local.architectAssessmentComplete());
        assertEquals(2000L, local.architectAssessmentGameTime());
        assertFalse(local.orsaDetectedAtAssessment());
        assertEquals(ReturnedHearthSavedData.HearthDisposition.WATCHFUL, major.mood());
    }

    @Test
    void orsaAssessmentMakesEveryHearthAgitatedButNotHostile() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        ReturnedHearthSavedData.AssessmentResult result = state.recordArchitectAssessment(
                player, major.id(), 2000L, true);

        assertTrue(result.completedNow());
        assertTrue(result.orsaDetected());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS,
                state.relationship(player));
        assertEquals(2, state.hearths().size());
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.HearthDisposition.AGITATED, hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.SUSPICIOUS,
                    hearth.violationState());
        }
    }

    @Test
    void completedAssessmentDoesNotReplayAndCannotDowngradeOrsathae() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.markPlayerOrsathae(player, major.id(), 1500L);

        ReturnedHearthSavedData.AssessmentResult first = state.recordArchitectAssessment(
                player, major.id(), 2000L, true);
        ReturnedHearthSavedData.AssessmentResult duplicate = state.recordArchitectAssessment(
                player, major.id(), 3000L, false);

        assertTrue(first.completedNow());
        assertFalse(duplicate.completedNow());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                state.relationship(player));
    }

    @Test
    void architectAssessmentPersistsAndCanBeResetForDebug() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.recordArchitectAssessment(player, major.id(), 2000L, true);

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        ReturnedHearthSavedData.HearthContactMemory local = restored.playerContact(player)
                .orElseThrow();
        assertTrue(local.architectAssessmentComplete());
        assertTrue(local.orsaDetectedAtAssessment());
        assertEquals(2000L, local.architectAssessmentGameTime());
        assertTrue(loaded.clearArchitectAssessmentForDebug(player, restored.id()));
        assertFalse(local.architectAssessmentComplete());
        assertFalse(local.orsaDetectedAtAssessment());
    }

    @Test
    void firstTransmissionIsPerPlayerAndPersistsOnlyAfterAssessment() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID assessedPlayer = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();

        assertFalse(state.completeFirstTransmission(
                assessedPlayer, major.id(), 1900L));
        state.recordArchitectAssessment(assessedPlayer, major.id(), 2000L, true);
        state.recordArchitectAssessment(otherPlayer, major.id(), 2100L, false);
        assertTrue(state.completeFirstTransmission(
                assessedPlayer, major.id(), 2200L));
        assertFalse(state.completeFirstTransmission(
                assessedPlayer, major.id(), 2300L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        ReturnedHearthSavedData.HearthContactMemory assessed = restored
                .playerContact(assessedPlayer).orElseThrow();
        ReturnedHearthSavedData.HearthContactMemory other = restored
                .playerContact(otherPlayer).orElseThrow();
        assertTrue(assessed.firstTransmissionComplete());
        assertEquals(2200L, assessed.firstTransmissionGameTime());
        assertFalse(other.firstTransmissionComplete());
        assertEquals(-1L, other.firstTransmissionGameTime());
        assertTrue(restored.firstTransmissionFired());
    }

    @Test
    void hearthMythRequiresFirstContactAndPersistsIndependently() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        state.recordArchitectAssessment(player, major.id(), 2000L, false);
        assertFalse(state.completeHearthMythTransmission(player, major.id(), 2100L));
        assertTrue(state.completeFirstTransmission(player, major.id(), 2200L));
        assertTrue(state.completeHearthMythTransmission(player, major.id(), 2600L));
        assertFalse(state.completeHearthMythTransmission(player, major.id(), 2700L));

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthContactMemory contact = major(loaded)
                .playerContact(player).orElseThrow();
        assertTrue(contact.firstTransmissionComplete());
        assertTrue(contact.hearthMythTransmissionComplete());
        assertEquals(2600L, contact.hearthMythTransmissionGameTime());
    }

    @Test
    void preMythSchemaLoadsWithMythUndelivered() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.recordArchitectAssessment(player, major.id(), 2000L, false);
        state.completeFirstTransmission(player, major.id(), 2200L);
        CompoundTag versionEleven = state.save(new CompoundTag(), null);
        versionEleven.putInt("dataVersion", 11);
        for (Tag entry : versionEleven.getList("hearths", Tag.TAG_COMPOUND)) {
            CompoundTag hearth = (CompoundTag) entry;
            for (Tag contactEntry : hearth.getList("playerContacts", Tag.TAG_COMPOUND)) {
                CompoundTag contact = (CompoundTag) contactEntry;
                contact.remove("hearthMythTransmissionComplete");
                contact.remove("hearthMythTransmissionGameTime");
            }
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionEleven, null);
        ReturnedHearthSavedData.HearthContactMemory contact = major(loaded)
                .playerContact(player).orElseThrow();
        assertTrue(contact.firstTransmissionComplete());
        assertFalse(contact.hearthMythTransmissionComplete());
        assertEquals(-1L, contact.hearthMythTransmissionGameTime());
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
    }

    @Test
    void transmissionDebugResetReopensDeliveryWithoutResettingAssessment() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.recordArchitectAssessment(player, major.id(), 2000L, false);
        state.completeFirstTransmission(player, major.id(), 2200L);

        assertTrue(state.clearFirstTransmissionForDebug(player, major.id()));
        ReturnedHearthSavedData.HearthContactMemory local = major.playerContact(player)
                .orElseThrow();
        assertTrue(local.architectAssessmentComplete());
        assertFalse(local.firstTransmissionComplete());
        assertEquals(-1L, local.firstTransmissionGameTime());
        assertFalse(major.firstTransmissionFired());
        assertFalse(state.clearFirstTransmissionForDebug(player, major.id()));
    }

    @Test
    void versionFiveAssessmentMemoryMigratesWithUndeliveredTransmission() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.recordArchitectAssessment(player, major.id(), 2000L, true);
        CompoundTag versionFive = state.save(new CompoundTag(), null);
        versionFive.putInt("dataVersion", 5);
        ListTag hearths = versionFive.getList("hearths", Tag.TAG_COMPOUND);
        for (Tag entry : hearths) {
            CompoundTag hearth = (CompoundTag) entry;
            ListTag contacts = hearth.getList("playerContacts", Tag.TAG_COMPOUND);
            for (Tag contactEntry : contacts) {
                CompoundTag contact = (CompoundTag) contactEntry;
                contact.remove("firstTransmissionComplete");
                contact.remove("firstTransmissionGameTime");
            }
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionFive, null);
        ReturnedHearthSavedData.HearthContactMemory local = major(loaded)
                .playerContact(player).orElseThrow();
        assertTrue(local.architectAssessmentComplete());
        assertFalse(local.firstTransmissionComplete());
        assertEquals(-1L, local.firstTransmissionGameTime());
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
    }

    @Test
    void protectedContainerViolationPersistsLocallyAndClassifiesTheHiveGlobally() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();

        ReturnedHearthSavedData.ViolationResult result = state.recordHearthViolation(
                player, major.id(), 2000L,
                ReturnedHearthSavedData.HearthViolationReason.PROTECTED_CONTAINER);

        assertTrue(result.changed());
        assertTrue(result.localReasonRecorded());
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                state.relationship(player));
        assertTrue(major.lootTaken());
        ReturnedHearthSavedData.HearthContactMemory local = major.playerContact(player)
                .orElseThrow();
        assertTrue(local.violationReasons().contains(
                ReturnedHearthSavedData.HearthViolationReason.PROTECTED_CONTAINER));
        assertEquals(2000L, local.firstViolationGameTime());
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.HearthDisposition.HOSTILE, hearth.mood());
            assertEquals(ReturnedHearthSavedData.ViolationState.VIOLATED,
                    hearth.violationState());
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        ReturnedHearthSavedData.HearthRecord restored = major(loaded);
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                loaded.relationship(player));
        assertTrue(restored.lootTaken());
        assertTrue(restored.playerContact(player).orElseThrow().violationReasons().contains(
                ReturnedHearthSavedData.HearthViolationReason.PROTECTED_CONTAINER));
    }

    @Test
    void violationDebugResetClearsLocalReasonsAndRestoresNeutralConduct() {
        ReturnedHearthSavedData state = selectedStateWithMinor(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.recordHearthViolation(player, major.id(), 2000L,
                ReturnedHearthSavedData.HearthViolationReason.PROTECTED_ENTRY);

        assertTrue(state.clearPlayerViolationsForDebug(player));
        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                state.relationship(player));
        assertTrue(major.playerContact(player).orElseThrow().violationReasons().isEmpty());
        assertEquals(-1L, major.playerContact(player).orElseThrow().firstViolationGameTime());
        assertFalse(major.lootTaken());
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            assertEquals(ReturnedHearthSavedData.ViolationState.NONE,
                    hearth.violationState());
        }
    }

    @Test
    void surveyObservationCataloguesAndPersistsTheHearth() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);

        ReturnedHearthSavedData.DiscoveryResult first = state.recordSurveyObservation(
                major.id(), 0.78F, true);
        ReturnedHearthSavedData.DiscoveryResult duplicate = state.recordSurveyObservation(
                major.id(), 0.52F, true);

        assertTrue(first.changed());
        assertTrue(first.newlyDiscovered());
        assertTrue(first.discovered());
        assertEquals(0.78F, first.signalStrength(), 0.0001F);
        assertFalse(duplicate.changed());
        assertFalse(duplicate.newlyDiscovered());

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(
                state.save(new CompoundTag(), null), null);
        assertTrue(major(loaded).discovered());
        assertEquals(0.78F, major(loaded).signalStrength(), 0.0001F);

        assertEquals(1, loaded.resetSurveyDiscoveryForDebug());
        assertFalse(major(loaded).discovered());
        assertEquals(0.0F, major(loaded).signalStrength(), 0.0001F);
    }

    @Test
    void versionSixEntityAttackMigratesIntoNamedViolationMemory() {
        ReturnedHearthSavedData state = selectedState(1000L);
        ReturnedHearthSavedData.HearthRecord major = major(state);
        UUID player = UUID.randomUUID();
        state.markPlayerOrsathae(player, major.id(), 2000L);
        CompoundTag versionSix = state.save(new CompoundTag(), null);
        versionSix.putInt("dataVersion", 6);
        ListTag hearths = versionSix.getList("hearths", Tag.TAG_COMPOUND);
        for (Tag entry : hearths) {
            CompoundTag hearth = (CompoundTag) entry;
            ListTag contacts = hearth.getList("playerContacts", Tag.TAG_COMPOUND);
            for (Tag contactEntry : contacts) {
                CompoundTag contact = (CompoundTag) contactEntry;
                contact.remove("violationReasons");
                contact.remove("firstViolationGameTime");
            }
        }

        ReturnedHearthSavedData loaded = ReturnedHearthSavedData.load(versionSix, null);
        ReturnedHearthSavedData.HearthContactMemory local = major(loaded)
                .playerContact(player).orElseThrow();
        assertTrue(local.attackedWatcher());
        assertTrue(local.violationReasons().contains(
                ReturnedHearthSavedData.HearthViolationReason.ENTITY_ATTACK));
        assertEquals(-1L, local.firstViolationGameTime());
        assertEquals(ReturnedHearthSavedData.CURRENT_DATA_VERSION, loaded.dataVersion());
    }

    private static ReturnedHearthSavedData selectedState(long gameTime) {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(998877L, anchor), gameTime);
        return state;
    }

    private static ReturnedHearthSavedData selectedStateWithMinor(long gameTime) {
        BlockPos anchor = new BlockPos(12, 70, -24);
        for (long seed = 0L; seed < 10_000L; seed++) {
            HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(seed, anchor);
            if (plan.minor().isEmpty()) {
                continue;
            }
            ReturnedHearthSavedData state = new ReturnedHearthSavedData();
            state.rememberTransponderAnchor(anchor);
            state.applySelectionPlan(plan, gameTime);
            return state;
        }
        throw new AssertionError("Could not find deterministic selection seed with a Minor Hearth");
    }

    private static ReturnedHearthSavedData.HearthRecord major(ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }

    private static void destroyAllHeartNodes(
            ReturnedHearthSavedData state, UUID hearthId) {
        for (int node = 0; node < com.frozendawn.homo.HeartLattice.NODE_COUNT; node++) {
            for (int hit = 0; hit < com.frozendawn.homo.HeartLattice.HITS_PER_NODE; hit++) {
                assertTrue(state.damageHeartMemoryNode(hearthId, node).accepted());
            }
        }
    }
}
