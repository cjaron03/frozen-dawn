package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(HearthReconciliationPolicy.FORMED_MAX_SURFACE_VARIANCE,
                minorPlan.maxSurfaceVariance());
        org.junit.jupiter.api.Assertions.assertTrue(
                majorPlan.maxSurfaceVariance() > 2,
                "Hearths should tolerate ordinary sloped terrain");
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

    @Test
    void degradedScenesStayEligibleUntilTheyAreAccepted() {
        // The gate: a pass that ended with structural holes leaves structurePlaced false, so
        // the hearth keeps being requeued and can heal once the obstruction is gone.
        assertTrue(HearthReconciliationPolicy.MAX_STRUCTURE_REAUDITS > 0);

        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(16, 70, -16);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(4242L, anchor), 10L);
        state.advanceMaturationForDebug(HearthMaturationPolicy.TRACE_START_TICKS, 1000L);
        ReturnedHearthSavedData.HearthRecord hearth = state.hearths().stream()
                .filter(record -> record.type() == HearthSelectionPolicy.HearthType.MAJOR)
                .findFirst()
                .orElseThrow();

        HearthReconciliationPolicy.StructurePlan plan =
                HearthReconciliationPolicy.desiredPlan(hearth);
        assertNotNull(plan);

        state.recordStructureProgress(hearth.id(), plan.version(), 48, plan.stage(), false);
        state.recordDegradedPlacement(hearth.id(), plan.version(), 12);
        state.scheduleStructureReaudit(hearth.id(), plan.version(), 12, plan.stage());
        assertTrue(HearthReconciliationPolicy.needsReconciliation(hearth));
        assertEquals(12, HearthReconciliationPolicy.resumeCursor(hearth));

        state.acceptDegradedStructure(hearth.id(), plan.version(), 48, plan.stage());
        assertFalse(HearthReconciliationPolicy.needsReconciliation(hearth));
    }
}
