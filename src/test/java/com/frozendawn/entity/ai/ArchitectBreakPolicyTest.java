package com.frozendawn.entity.ai;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectBreakPolicyTest {
    private static final EmptyBlockGetter LEVEL = EmptyBlockGetter.INSTANCE;
    private static final BlockPos POS = BlockPos.ZERO;

    @Test
    void frozenAtmosphereIsPerceivedAsAThinDrySurfaceWithoutBecomingABreachTarget() {
        BlockState state = ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState();

        assertFalse(ArchitectBreakPolicy.isObstructiveForArchitect(state, LEVEL, POS));
        assertTrue(ArchitectBreakPolicy.isDryPassableForArchitect(state, LEVEL, POS));
        assertTrue(ArchitectBreakPolicy.isThinTraversableSurface(state, LEVEL, POS));
        assertEquals(0.125D,
                ArchitectBreakPolicy.traversableSurfaceOffset(state, LEVEL, POS),
                0.0001D);
    }

    @Test
    void shallowSnowIsTraversableButHalfHeightSnowRemainsBreachable() {
        BlockState shallow = Blocks.SNOW.defaultBlockState()
                .setValue(SnowLayerBlock.LAYERS, 2);
        BlockState halfHeight = shallow.setValue(SnowLayerBlock.LAYERS, 5);

        assertTrue(ArchitectBreakPolicy.isThinTraversableSurface(shallow, LEVEL, POS));
        assertFalse(ArchitectBreakPolicy.isObstructiveForArchitect(shallow, LEVEL, POS));
        assertTrue(ArchitectBreakPolicy.isObstructiveForArchitect(halfHeight, LEVEL, POS));
        assertFalse(ArchitectBreakPolicy.isDryPassableForArchitect(halfHeight, LEVEL, POS));
    }

    @Test
    void fluidsAndFullBlocksNeverBecomeDryPassageSpace() {
        assertFalse(ArchitectBreakPolicy.isDryPassableForArchitect(
                Blocks.WATER.defaultBlockState(), LEVEL, POS));
        assertFalse(ArchitectBreakPolicy.isDryPassableForArchitect(
                Blocks.STONE.defaultBlockState(), LEVEL, POS));
        assertTrue(ArchitectBreakPolicy.isDryPassableForArchitect(
                Blocks.AIR.defaultBlockState(), LEVEL, POS));
    }
}
