package com.frozendawn.data;

import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthSelectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

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
        assertEquals(original.globalDisposition(), loaded.globalDisposition());
        assertEquals(original.permanentOrsathae(), loaded.permanentOrsathae());
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

    private static ReturnedHearthSavedData selectedState(long gameTime) {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(998877L, anchor), gameTime);
        return state;
    }

    private static ReturnedHearthSavedData.HearthRecord major(ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }
}
