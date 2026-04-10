package com.frozendawn.block;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PhaseBarometerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 5.0, 1.0, 12.0),
            Block.box(11.0, 0.0, 3.0, 13.0, 1.0, 12.0),
            Block.box(2.0, 1.0, 3.0, 14.0, 10.0, 13.0),
            Block.box(3.0, 2.0, 1.0, 13.0, 11.0, 4.0),
            Block.box(3.0, 10.0, 4.0, 13.0, 12.0, 12.0),
            Block.box(4.0, 12.0, 8.0, 12.0, 13.0, 13.0),
            Block.box(10.0, 12.0, 9.0, 12.0, 13.0, 11.0),
            Block.box(10.75, 13.0, 9.75, 11.25, 16.0, 10.25)
    );
    private static final VoxelShape SOUTH_SHAPE = rotate(NORTH_SHAPE, 2);
    private static final VoxelShape WEST_SHAPE = rotate(NORTH_SHAPE, 3);
    private static final VoxelShape EAST_SHAPE = rotate(NORTH_SHAPE, 1);

    public PhaseBarometerBlock(Properties properties) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static VoxelShape rotate(VoxelShape shape, int quarterTurns) {
        VoxelShape rotated = shape;
        for (int i = 0; i < quarterTurns; i++) {
            VoxelShape[] buffer = new VoxelShape[]{Shapes.empty()};
            rotated.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    buffer[0] = Shapes.or(
                            buffer[0],
                            Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)
                    ));
            rotated = buffer[0];
        }
        return rotated;
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhaseBarometerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.PHASE_BAROMETER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((PhaseBarometerBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PhaseBarometerBlockEntity barometer) {
            serverPlayer.openMenu(barometer, pos);
            WorldTickHandler.grantAdvancement(serverPlayer, "reading_the_sky");
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return super.getDestroyProgress(state, player, level, pos);
    }
}
