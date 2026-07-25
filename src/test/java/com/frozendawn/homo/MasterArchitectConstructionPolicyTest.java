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

    @Test
    void constructionDirectorPrioritizesPlayerBehavior() {
        assertEquals(
                MasterArchitectConstructionPolicy.ConstructionIntent.COVER_DENIAL,
                MasterArchitectConstructionPolicy.chooseIntent(
                        true, true, 80, 400.0D, 0));
        assertEquals(
                MasterArchitectConstructionPolicy.ConstructionIntent.HEATER_BURIAL,
                MasterArchitectConstructionPolicy.chooseIntent(
                        false, true, 80, 400.0D, 0));
        assertEquals(
                MasterArchitectConstructionPolicy.ConstructionIntent.ENCLOSURE,
                MasterArchitectConstructionPolicy.chooseIntent(
                        false, false, 30, 400.0D, 0));
        assertEquals(
                MasterArchitectConstructionPolicy.ConstructionIntent.VANTAGE,
                MasterArchitectConstructionPolicy.chooseIntent(
                        false, false, 0, 100.0D, 1));
        assertEquals(
                MasterArchitectConstructionPolicy.ConstructionIntent.HEATER_BURIAL,
                MasterArchitectConstructionPolicy.chooseIntent(
                        false, false, 0, 25.0D, 2));
    }

    @Test
    void crowdedTerrainCanStillProduceReadablePartialStructures() {
        assertTrue(MasterArchitectConstructionPolicy.hasViableStructure(
                7, true, MasterArchitectConstructionPolicy.MIN_OPENING_COLUMNS));
        assertFalse(MasterArchitectConstructionPolicy.hasViableStructure(
                6, true, MasterArchitectConstructionPolicy.MIN_OPENING_COLUMNS));
        assertFalse(MasterArchitectConstructionPolicy.hasViableStructure(
                11, false, MasterArchitectConstructionPolicy.MIN_OPENING_COLUMNS));
        assertTrue(MasterArchitectConstructionPolicy.hasViableStructure(
                3, true, MasterArchitectConstructionPolicy.MIN_WALL_COLUMNS));
    }

    @Test
    void shelterHealingScalesByPresetAndStopsAtPhaseCeiling() {
        assertTrue(MasterArchitectConstructionPolicy.shelterHealGraceTicks("brutal")
                < MasterArchitectConstructionPolicy.shelterHealGraceTicks("normal"));
        assertTrue(MasterArchitectConstructionPolicy.shelterHealGraceTicks("normal")
                < MasterArchitectConstructionPolicy.shelterHealGraceTicks("cinematic"));
        assertTrue(MasterArchitectConstructionPolicy.shelterHealPerTick(450.0F, "brutal")
                > MasterArchitectConstructionPolicy.shelterHealPerTick(450.0F, "normal"));
        assertTrue(MasterArchitectConstructionPolicy.shelterHealPerTick(450.0F, "normal")
                > MasterArchitectConstructionPolicy.shelterHealPerTick(450.0F, "cinematic"));
        assertEquals(
                337.5F,
                MasterArchitectConstructionPolicy.shelterHealCeiling(
                        MasterArchitectCombatPhase.CONSTRUCTION, 450.0F),
                0.001F);
        assertEquals(
                225.0F,
                MasterArchitectConstructionPolicy.shelterHealCeiling(
                        MasterArchitectCombatPhase.TETHER, 450.0F),
                0.001F);
        assertEquals(
                135.0F,
                MasterArchitectConstructionPolicy.shelterHealCeiling(
                        MasterArchitectCombatPhase.ASCENT, 450.0F),
                0.001F);
        assertEquals(
                45.0F,
                MasterArchitectConstructionPolicy.shelterHealCeiling(
                        MasterArchitectCombatPhase.FLOOD, 450.0F),
                0.001F);
    }
}
