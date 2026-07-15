package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HearthReconciliationPolicyTest {

    @Test
    void onlyIntactMajorReceivesTheVillageScalePlan() {
        ReturnedHearthSavedData state = selectedStateWithMinor();
        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 1000L);

        HearthReconciliationPolicy.StructurePlan majorPlan = HearthReconciliationPolicy
                .desiredPlan(state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow());
        HearthReconciliationPolicy.StructurePlan minorPlan = HearthReconciliationPolicy
                .desiredPlan(state.hearth(HearthSelectionPolicy.HearthType.MINOR).orElseThrow());

        assertEquals(HearthReconciliationPolicy.INTACT_PLAN_VERSION, majorPlan.version());
        assertEquals(ReturnedHearthSavedData.HearthStage.INTACT, majorPlan.stage());
        assertEquals(HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS,
                majorPlan.footprintRadius());
        assertEquals(HearthReconciliationPolicy.INTACT_MAX_SURFACE_VARIANCE,
                majorPlan.maxSurfaceVariance());

        assertEquals(HearthReconciliationPolicy.FORMED_PLAN_VERSION, minorPlan.version());
        assertEquals(ReturnedHearthSavedData.HearthStage.FORMED, minorPlan.stage());
        assertEquals(HearthReconciliationPolicy.FORMED_FOOTPRINT_RADIUS,
                minorPlan.footprintRadius());
    }

    @Test
    void intactUpgradeRestartsAtTheBeginningOfTheNewPlan() {
        ReturnedHearthSavedData state = selectedStateWithMinor();
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
        state.advanceMaturationForDebug(HearthMaturationPolicy.FORMED_START_TICKS, 1000L);
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.FORMED_PLAN_VERSION,
                FormedHearthLayout.create(major.layoutSeed(), major.type()).size(),
                ReturnedHearthSavedData.HearthStage.FORMED, true);

        state.advanceMaturationForDebug(
                HearthMaturationPolicy.INTACT_START_TICKS
                        - HearthMaturationPolicy.FORMED_START_TICKS,
                1000L);

        assertEquals(0, HearthReconciliationPolicy.resumeCursor(major));
        assertEquals(ReturnedHearthSavedData.HearthStage.INTACT, major.stage());
    }

    private static ReturnedHearthSavedData selectedStateWithMinor() {
        BlockPos anchor = new BlockPos(12, 70, -24);
        for (long seed = 0L; seed < 100L; seed++) {
            HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(
                    seed, anchor);
            if (plan.minor().isEmpty()) {
                continue;
            }
            ReturnedHearthSavedData state = new ReturnedHearthSavedData();
            state.applySelectionPlan(plan, 1000L);
            return state;
        }
        throw new AssertionError("Expected a deterministic seed with a Minor Hearth");
    }
}
