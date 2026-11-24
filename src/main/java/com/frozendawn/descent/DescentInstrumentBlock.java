package com.frozendawn.descent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * An era's instrument (CH-46). Using it plays the era's clip once at the block. It is never a redstone or
 * note-block instrument, and has no block entity.
 */
public class DescentInstrumentBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Set while the clip plays; a scheduled tick clears it once the clip's length has passed. */
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");
    private final DescentEra era;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /** {@code bounds} is the model's footprint as it faces north: minX, minY, minZ, maxX, maxY, maxZ in pixels. */
    public DescentInstrumentBlock(DescentEra era, double[] bounds, Properties properties) {
        super(properties);
        this.era = era;
        for (Direction facing : Direction.Plane.HORIZONTAL) shapes.put(facing, turned(bounds, facing));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PLAYING, false));
    }

    public DescentEra era() {
        return era;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && !state.getValue(PLAYING)) {
            level.setBlock(pos, state.setValue(PLAYING, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, era.clipTicks());
            level.playSound(null, pos, era.sound().value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PLAYING)) level.setBlock(pos, state.setValue(PLAYING, false), Block.UPDATE_CLIENTS);
    }

    /** The north-facing box turned as the blockstate turns the model (y 90 per step, clockwise from above). */
    private static VoxelShape turned(double[] b, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }
}
