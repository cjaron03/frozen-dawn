package com.frozendawn.homo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class HearthReconciliationManagerTest {
    @Test
    void foundationSupportsCanPackThroughWaterButNotLava() {
        assertTrue(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.WATER.defaultBlockState(), HearthStructurePiece.FOUNDATION_SUPPORT));
        assertFalse(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.LAVA.defaultBlockState(), HearthStructurePiece.FOUNDATION_SUPPORT));
    }

    @Test
    void ordinaryStructurePiecesStillRejectWater() {
        assertFalse(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.WATER.defaultBlockState(), HearthStructurePiece.FROZEN_PLANKS));
        assertTrue(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.AIR.defaultBlockState(), HearthStructurePiece.FROZEN_PLANKS));
    }

    @Test
    void lowerDeckCanSealWaterAfterItsFoundationSupportsArePlaced() {
        HearthStructurePlacement floor = new HearthStructurePlacement(
                HearthStructurePiece.PACKED_ICE_LOWER,
                new BlockPos(10, -1, 6),
                Direction.NORTH,
                0,
                HearthStructurePlacement.Protection.NONE);
        HearthStructurePlacement wall = new HearthStructurePlacement(
                HearthStructurePiece.FROZEN_PLANKS,
                new BlockPos(10, 0, 6),
                Direction.NORTH,
                0,
                HearthStructurePlacement.Protection.STRUCTURE);

        assertTrue(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.WATER.defaultBlockState(), floor));
        assertFalse(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.WATER.defaultBlockState(), wall));
        assertFalse(HearthReconciliationManager.allowsFluidReplacement(
                Blocks.LAVA.defaultBlockState(), floor));
    }

    @Test
    void lowerDeckCanReplaceNaturalGravelDuringIntactExpansion() {
        HearthStructurePlacement floor = new HearthStructurePlacement(
                HearthStructurePiece.PACKED_ICE_LOWER,
                new BlockPos(10, -1, 6),
                Direction.NORTH,
                0,
                HearthStructurePlacement.Protection.NONE);
        HearthStructurePlacement aboveGround = new HearthStructurePlacement(
                HearthStructurePiece.PACKED_ICE_LOWER,
                new BlockPos(10, 0, 6),
                Direction.NORTH,
                0,
                HearthStructurePlacement.Protection.NONE);

        assertTrue(HearthReconciliationManager.canReplaceNatural(
                Blocks.GRAVEL.defaultBlockState(), floor));
        assertFalse(HearthReconciliationManager.canReplaceNatural(
                Blocks.GRAVEL.defaultBlockState(), aboveGround));
    }

    @Test
    void unchangedBlocksDoNotRequirePlacementOrEntityClearance() {
        assertFalse(HearthReconciliationManager.needsPlacement(
                Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState()));
        assertTrue(HearthReconciliationManager.needsPlacement(
                Blocks.AIR.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState()));
    }

    @Test
    void blockedClearanceCellsCanBePreservedWithoutStallingTheScene() {
        assertTrue(HearthReconciliationManager.isClearancePiece(
                HearthStructurePiece.CLEAR_TRANSIENT));
        assertTrue(HearthReconciliationManager.isClearancePiece(
                HearthStructurePiece.CLEAR_SETTLEMENT));
        assertTrue(HearthReconciliationManager.isClearancePiece(
                HearthStructurePiece.CLEAR_PLATFORM));
        assertTrue(HearthReconciliationManager.isClearancePiece(
                HearthStructurePiece.CLEAR_LEGACY));
        assertFalse(HearthReconciliationManager.isClearancePiece(
                HearthStructurePiece.BOUNDARY_MARKER));
    }

    @Test
    void optionalTerrainDecorationsCannotStallAnOtherwiseCompleteHearth() {
        assertTrue(HearthReconciliationManager.isOptionalTerrainDecoration(
                HearthStructurePiece.SNOW_MARKER));
        assertTrue(HearthReconciliationManager.isOptionalTerrainDecoration(
                HearthStructurePiece.FROZEN_ATMOSPHERE));
        assertFalse(HearthReconciliationManager.isOptionalTerrainDecoration(
                HearthStructurePiece.FROZEN_STONE_BRICKS));
        assertFalse(HearthReconciliationManager.isOptionalTerrainDecoration(
                HearthStructurePiece.SACRED_CHEST));
    }
}
