package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthPopulationPolicyTest {

    @Test
    void onlyCompletedIntactMajorHearthCanHostPopulation() {
        ReturnedHearthSavedData state = selectedState();
        ReturnedHearthSavedData.HearthRecord major = major(state);
        assertFalse(HearthPopulationPolicy.canHostPopulation(major));

        state.advanceMaturationForDebug(HearthMaturationPolicy.INTACT_START_TICKS, 1000L);
        state.resolveSurface(major.id(), new BlockPos(major.center().getX(), 70, major.center().getZ()));
        state.recordStructureProgress(major.id(), HearthReconciliationPolicy.INTACT_PLAN_VERSION,
                10_000, ReturnedHearthSavedData.HearthStage.INTACT, true);

        assertTrue(HearthPopulationPolicy.canHostPopulation(major));
        state.hearth(HearthSelectionPolicy.HearthType.MINOR).ifPresent(minor ->
                assertFalse(HearthPopulationPolicy.canHostPopulation(minor)));
    }

    @Test
    void everyRoleHasAStableUniqueIntactAnchor() {
        long seed = 424242L;
        Set<BlockPos> anchors = new HashSet<>();
        for (HearthPopulationRole role : HearthPopulationRole.values()) {
            BlockPos first = HearthPopulationPolicy.anchorOffset(role, seed);
            BlockPos second = HearthPopulationPolicy.anchorOffset(role, seed);
            assertEquals(first, second);
            assertTrue(anchors.add(first));
            assertTrue(Math.abs(first.getX())
                    <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS);
            assertTrue(Math.abs(first.getZ())
                    <= HearthReconciliationPolicy.INTACT_FOOTPRINT_RADIUS);
        }
        assertEquals(EnumSet.allOf(HearthPopulationRole.class).size(), anchors.size());
    }

    @Test
    void replacementDelayUsesAnInclusiveBoundary() {
        assertFalse(HearthPopulationPolicy.isReplacementReady(2200L, 2199L));
        assertTrue(HearthPopulationPolicy.isReplacementReady(2200L, 2200L));
        assertTrue(HearthPopulationPolicy.isReplacementReady(-1L, 0L));
    }

    @Test
    void onlyPermanentOrsathaeIsHostile() {
        assertFalse(HearthPopulationPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL));
        assertFalse(HearthPopulationPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS));
        assertTrue(HearthPopulationPolicy.isHostileRelationship(
                ReturnedHearthSavedData.HiveRelationship.ORSATHAE));
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
