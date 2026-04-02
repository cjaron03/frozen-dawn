package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
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

public class TownPASpeakerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0),
            Block.box(5.0, 4.0, 6.0, 11.0, 9.0, 11.0),
            Block.box(4.0, 5.0, 1.0, 12.0, 11.0, 6.0)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0),
            Block.box(5.0, 4.0, 5.0, 11.0, 9.0, 10.0),
            Block.box(4.0, 5.0, 10.0, 12.0, 11.0, 15.0)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0),
            Block.box(6.0, 4.0, 5.0, 11.0, 9.0, 11.0),
            Block.box(1.0, 5.0, 4.0, 6.0, 11.0, 12.0)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0),
            Block.box(5.0, 4.0, 5.0, 10.0, 9.0, 11.0),
            Block.box(10.0, 5.0, 4.0, 15.0, 11.0, 12.0)
    );

    public TownPASpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        return below.isFaceSturdy(level, belowPos, Direction.UP)
                || below.is(ModBlocks.STREET_LIGHT.get())
                || below.is(Blocks.IRON_BARS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
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
        return new TownPASpeakerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.TOWN_PA_SPEAKER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((TownPASpeakerBlockEntity) be).serverTick();
    }
}
