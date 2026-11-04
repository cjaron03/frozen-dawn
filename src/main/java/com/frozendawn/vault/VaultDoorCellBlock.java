package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The part of a vault door that stops you. Never drawn in the world: its model is the whole panel, which the door's
 * mechanism draws sliding (three by three blocks, so one model holds it). */
public final class VaultDoorCellBlock extends Block {
    public static final BooleanProperty LAST = BooleanProperty.create("last");

    public VaultDoorCellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAST, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LAST); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1; }
}
