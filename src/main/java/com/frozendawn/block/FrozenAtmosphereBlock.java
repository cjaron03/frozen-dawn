package com.frozendawn.block;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Frozen Atmosphere deposit: thin pale blue-white layer that forms on
 * surface blocks during phase 6 late. Sublimates if temperature rises
 * above -150C. Drops Frozen Atmosphere Shard when mined.
 */
public class FrozenAtmosphereBlock extends Block {
    public static final BooleanProperty DARK = BooleanProperty.create("dark");

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public FrozenAtmosphereBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DARK, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DARK);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof ArchitectEntity) {
            return Shapes.empty();
        }
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof Level realLevel
                && FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(realLevel, pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
