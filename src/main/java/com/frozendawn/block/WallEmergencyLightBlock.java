package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WallEmergencyLightBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5.0, 5.0, 13.0, 11.0, 11.0, 16.0),
            Block.box(4.0, 4.0, 9.0, 12.0, 12.0, 13.0),
            Block.box(5.0, 5.0, 4.0, 11.0, 11.0, 9.0)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 3.0),
            Block.box(4.0, 4.0, 3.0, 12.0, 12.0, 7.0),
            Block.box(5.0, 5.0, 7.0, 11.0, 11.0, 12.0)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(13.0, 5.0, 5.0, 16.0, 11.0, 11.0),
            Block.box(9.0, 4.0, 4.0, 13.0, 12.0, 12.0),
            Block.box(4.0, 5.0, 5.0, 9.0, 11.0, 11.0)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0, 5.0, 5.0, 3.0, 11.0, 11.0),
            Block.box(3.0, 4.0, 4.0, 7.0, 12.0, 12.0),
            Block.box(7.0, 5.0, 5.0, 12.0, 11.0, 11.0)
    );

    public WallEmergencyLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(EmergencyLightBlock.POWER_STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EmergencyLightBlock.POWER_STAGE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (!direction.getAxis().isHorizontal()) {
                continue;
            }
            BlockState state = defaultBlockState().setValue(FACING, direction);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos anchorPos = pos.relative(facing.getOpposite());
        return level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmergencyLightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.EMERGENCY_LIGHT.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((EmergencyLightBlockEntity) be).serverTick();
    }
}
