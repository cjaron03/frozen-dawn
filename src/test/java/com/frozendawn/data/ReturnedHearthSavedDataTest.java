package com.frozendawn.data;

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
}
