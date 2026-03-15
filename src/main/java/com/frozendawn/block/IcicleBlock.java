package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hanging ice spike that forms under frozen overhangs in late apocalypse phases.
 *
 * AGE controls the visible length of the icicle rather than using a full
 * multi-block dripstone chain. This keeps the block lightweight while still
 * reading as a real hanging icicle in-world.
 */
public class IcicleBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape SHAPE_0 = Block.box(6, 12, 6, 10, 16, 10);
    private static final VoxelShape SHAPE_1 = Block.box(5, 8, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_2 = Block.box(4, 4, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_3 = Block.box(3, 0, 3, 13, 16, 13);

    public IcicleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AGE)) {
            case 0 -> SHAPE_0;
            case 1 -> SHAPE_1;
            case 2 -> SHAPE_2;
            default -> SHAPE_3;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        return aboveState.isFaceSturdy(level, above, Direction.DOWN)
                || aboveState.is(Blocks.SNOW_BLOCK)
                || aboveState.is(Blocks.ICE)
                || aboveState.is(Blocks.PACKED_ICE)
                || aboveState.is(Blocks.BLUE_ICE)
                || aboveState.is(ModBlocks.FROZEN_DIRT.get())
                || aboveState.is(ModBlocks.FROZEN_SAND.get())
                || aboveState.is(ModBlocks.FROZEN_LOG.get())
                || aboveState.is(ModBlocks.FROZEN_LEAVES.get())
                || aboveState.is(ModBlocks.FROZEN_OBSIDIAN.get());
    }

    @Override
    protected BlockState updateShape(
            BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
