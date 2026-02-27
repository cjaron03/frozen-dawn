package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Frozen Atmosphere deposit: thin pale blue-white layer that forms on
 * surface blocks during phase 6 late. Sublimates if temperature rises
 * above -150C. Drops Frozen Atmosphere Shard when mined.
 */
public class FrozenAtmosphereBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public FrozenAtmosphereBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }
}
