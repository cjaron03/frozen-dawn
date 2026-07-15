package com.frozendawn.homo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
