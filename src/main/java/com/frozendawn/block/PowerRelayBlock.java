package com.frozendawn.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Passive link node. Relays do not seal rooms; the feedthrough is a full airtight block. */
public final class PowerRelayBlock extends Block {
    /** Points from the mounting surface toward the relay cap. UP preserves legacy floor relays. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = relayShapes();
    private final int range;
    private final boolean feedthrough;

    public PowerRelayBlock(Properties properties, int range, boolean feedthrough) {
        super(properties);
        this.range = range;
        this.feedthrough = feedthrough;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public int linkRange() { return range; }
    public boolean isFeedthrough() { return feedthrough; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, feedthrough ? Direction.UP : context.getClickedFace());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return feedthrough ? Shapes.block() : SHAPES.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> relayShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var facing : Direction.values()) {
            shapes.put(facing, Shapes.or(
                    orientedBox(facing, 2, 0, 2, 14, 3, 14),
                    orientedBox(facing, 6, 3, 6, 10, 13, 10),
                    orientedBox(facing, 4, 13, 4, 12, 16, 12)));
        }
        return Map.copyOf(shapes);
    }

    /** The same rotations used by the six blockstate model variants, around the block centre. */
    private static VoxelShape orientedBox(Direction facing, double x1, double y1, double z1,
            double x2, double y2, double z2) {
        return switch (facing) {
            case UP -> box(x1, y1, z1, x2, y2, z2);
            case DOWN -> box(x1, 16-y2, 16-z2, x2, 16-y1, 16-z1);
            case NORTH -> box(x1, z1, 16-y2, x2, z2, 16-y1);
            case SOUTH -> box(16-x2, z1, y1, 16-x1, z2, y2);
            case WEST -> box(16-y2, z1, 16-x2, 16-y1, z2, 16-x1);
            case EAST -> box(y1, z1, x1, y2, z2, x2);
        };
    }
}
