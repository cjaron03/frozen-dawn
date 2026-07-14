package com.frozendawn.data;

import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthSelectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
}
