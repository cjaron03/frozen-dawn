package com.frozendawn.descent;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * An era's redaction (CH-44): a full block whose front shows one bar exactly 10 characters wide in the era's
 * material. Blocks only, never letters (CH-10, CH-11).
 */
public class DescentMarkBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final DescentEra era;

    public DescentMarkBlock(DescentEra era, Properties properties) {
        super(properties);
        this.era = era;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DescentEra era() {
        return era;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
