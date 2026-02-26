package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Acheronite Crystal with 4 growth stages (AGE 0-3).
 * Forms on frozen substrates in phase 5+ at temperatures below -80C.
 * Only stage 3 (full cluster) drops shards when mined.
 * Not player-placeable (no BlockItem registered).
 */
public class AcheroniteCrystalBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape SHAPE_0 = Block.box(5, 0, 5, 11, 6, 11);   // small bud
    private static final VoxelShape SHAPE_1 = Block.box(4, 0, 4, 12, 10, 12);  // medium bud
    private static final VoxelShape SHAPE_2 = Block.box(3, 0, 3, 13, 14, 13);  // large bud
    private static final VoxelShape SHAPE_3 = Block.box(2, 0, 2, 14, 16, 14);  // full cluster

    public AcheroniteCrystalBlock(Properties properties) {
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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }
}
