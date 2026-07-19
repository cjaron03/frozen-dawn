package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectConstructionPolicyTest {

    @Test
    void wallCanOnlyStartDuringConstructionAtValidRange() {
        assertTrue(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, false, 12.0D * 12.0D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.KIT,
                0, false, 12.0D * 12.0D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.TETHER,
                0, false, 12.0D * 12.0D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.CONSTRUCTION,
                1, false, 12.0D * 12.0D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, true, 12.0D * 12.0D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, false, 24.1D * 24.1D, true));
        assertFalse(MasterArchitectConstructionPolicy.canStartWall(
                MasterArchitectCombatPhase.CONSTRUCTION,
                0, false, 12.0D * 12.0D, false));
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
