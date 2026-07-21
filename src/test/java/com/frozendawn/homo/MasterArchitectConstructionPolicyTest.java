package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectConstructionPolicyTest {

    @Test
    void wallCanOnlyStartDuringConstructionAtValidRange() {
        assertTrue(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, false, 12.0D * 12.0D));
        assertFalse(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.KIT,
                0, false, 12.0D * 12.0D));
        assertFalse(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.TETHER,
                0, false, 12.0D * 12.0D));
        assertFalse(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.CONSTRUCTION,
                1, false, 12.0D * 12.0D));
        assertFalse(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, true, 12.0D * 12.0D));
        assertFalse(MasterArchitectConstructionPolicy.canStartConstruction(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, false, 24.1D * 24.1D));
    }

    @Test
    void wallBuildsAnOpenBackedEnclosureWithSeamLast() {
        int[] normalOffsets = {-1, -1, 0, 0, 1, 1, 2, 2, 2, 2, 2};
        int[] tangentOffsets = {-2, 2, -2, 2, -2, 2, -2, 2, -1, 1, 0};
        for (int index = 0; index < normalOffsets.length; index++) {
            assertEquals(index,
                    MasterArchitectConstructionPolicy.columnIndexAtTick(
                            index + 1));
            assertEquals(normalOffsets[index],
                    MasterArchitectConstructionPolicy.columnNormalOffset(index));
            assertEquals(tangentOffsets[index],
                    MasterArchitectConstructionPolicy.columnTangentOffset(index));
            assertEquals(index == normalOffsets.length - 1,
                    MasterArchitectConstructionPolicy.isWeakSeamColumn(index));
        }
        assertEquals(-1,
                MasterArchitectConstructionPolicy.columnIndexAtTick(12));
        assertEquals(33, MasterArchitectConstructionPolicy.MAX_ACTIVE_BLOCKS);
    }

    @Test
    void anyMissingTrackedSeamCollapsesTheWall() {
        assertFalse(MasterArchitectConstructionPolicy
                .shouldCollapseForMissingSeam(0, 0));
        assertFalse(MasterArchitectConstructionPolicy
                .shouldCollapseForMissingSeam(3, 3));
        assertTrue(MasterArchitectConstructionPolicy
                .shouldCollapseForMissingSeam(3, 2));
    }

    @Test
    void sharedBudgetNeverExceedsSixtyFourLiveBlocks() {
        assertTrue(MasterArchitectConstructionPolicy.canReserve(33, 31));
        assertFalse(MasterArchitectConstructionPolicy.canReserve(33, 32));
        assertFalse(MasterArchitectConstructionPolicy.canReserve(64, 1));
        assertFalse(MasterArchitectConstructionPolicy.canReserve(0, 0));
    }

    @Test
    void seamCollapseLeavesSparseFloorRubbleAndCanStaggerNearbyMaster() {
        assertTrue(MasterArchitectConstructionPolicy.shouldLeaveRubble(
                0, 64, 64, false));
        assertFalse(MasterArchitectConstructionPolicy.shouldLeaveRubble(
                1, 64, 64, false));
        assertFalse(MasterArchitectConstructionPolicy.shouldLeaveRubble(
                4, 65, 64, false));
        assertFalse(MasterArchitectConstructionPolicy.shouldLeaveRubble(
                4, 64, 64, true));
        assertTrue(MasterArchitectConstructionPolicy.shouldStaggerMaster(25.0D));
        assertFalse(MasterArchitectConstructionPolicy.shouldStaggerMaster(25.1D));
    }

    @Test
    void wallPlaneFacesTheDominantTargetAxis() {
        assertEquals(
                new MasterArchitectConstructionPolicy.WallAxes(1, 0, 0, 1),
                MasterArchitectConstructionPolicy.wallAxes(6.0D, 2.0D));
        assertEquals(
                new MasterArchitectConstructionPolicy.WallAxes(-1, 0, 0, 1),
                MasterArchitectConstructionPolicy.wallAxes(-6.0D, 2.0D));
        assertEquals(
                new MasterArchitectConstructionPolicy.WallAxes(0, -1, 1, 0),
                MasterArchitectConstructionPolicy.wallAxes(2.0D, -6.0D));
    }
}
