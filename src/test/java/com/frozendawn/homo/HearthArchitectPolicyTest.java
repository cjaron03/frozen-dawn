package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthArchitectPolicyTest {

    @Test
    void onlyReconciledIntactMajorHearthCanHostAssessor() {
        ReturnedHearthSavedData state = selectedState();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertFalse(HearthArchitectPolicy.canHostAssessor(major));

        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 1000L);
        state.resolveSurface(major.id(), new BlockPos(major.center().getX(), 70, major.center().getZ()));
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.TRACE_PLAN_VERSION,
                100, ReturnedHearthSavedData.HearthStage.TRACE, true);

        assertTrue(HearthArchitectPolicy.canHostAssessor(major));
        state.hearth(HearthSelectionPolicy.HearthType.MINOR).ifPresent(minor ->
                assertFalse(HearthArchitectPolicy.canHostAssessor(minor)));
    }

    @Test
    void spawnRingIsDeterministicAndInsideHearthPerimeter() {
        List<BlockPos> first = HearthArchitectPolicy.spawnOffsets(424242L);
        List<BlockPos> second = HearthArchitectPolicy.spawnOffsets(424242L);

        assertEquals(first, second);
        assertEquals(HearthArchitectPolicy.SPAWN_ATTEMPTS, first.size());
        for (BlockPos offset : first) {
            double distance = Math.sqrt((double) offset.getX() * offset.getX()
                    + (double) offset.getZ() * offset.getZ());
            assertTrue(distance >= HearthArchitectPolicy.MIN_SPAWN_RADIUS - 1.0D);
            assertTrue(distance <= HearthArchitectPolicy.MAX_SPAWN_RADIUS + 1.0D);
            assertTrue(distance < HearthArchitectPolicy.HOME_RADIUS);
        }
    }

    @Test
    void assessmentDistanceUsesStableInclusiveBand() {
        assertFalse(HearthArchitectPolicy.isAssessmentDistance(9.99D * 9.99D));
        assertTrue(HearthArchitectPolicy.isAssessmentDistance(10.0D * 10.0D));
        assertTrue(HearthArchitectPolicy.isAssessmentDistance(24.0D * 24.0D));
        assertFalse(HearthArchitectPolicy.isAssessmentDistance(24.01D * 24.01D));
    }

    @Test
    void orsaDetectionEscalatesOnlyToSuspicion() {
        assertEquals(ReturnedHearthSavedData.HiveRelationship.NEUTRAL,
                HearthArchitectPolicy.relationshipAfterAssessment(
                        ReturnedHearthSavedData.HiveRelationship.NEUTRAL, false));
        assertEquals(ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS,
                HearthArchitectPolicy.relationshipAfterAssessment(
                        ReturnedHearthSavedData.HiveRelationship.NEUTRAL, true));
        assertEquals(ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                HearthArchitectPolicy.relationshipAfterAssessment(
                        ReturnedHearthSavedData.HiveRelationship.ORSATHAE, true));
    }

    private static ReturnedHearthSavedData selectedState() {
        ReturnedHearthSavedData state = new ReturnedHearthSavedData();
        BlockPos anchor = new BlockPos(12, 70, -24);
        state.rememberTransponderAnchor(anchor);
        state.applySelectionPlan(HearthSelectionPolicy.createPlan(998877L, anchor), 1000L);
        return state;
    }

    private static ReturnedHearthSavedData.HearthRecord major(ReturnedHearthSavedData state) {
        return state.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElseThrow();
    }
}
