package com.frozendawn.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Plasma-facing ring around the coil column. WEAR climbs with disruptions; a worn wall drops nothing. */
public final class FirstWallBlock extends Block {
    public static final int MAX_WEAR = 20;
    public static final IntegerProperty WEAR = IntegerProperty.create("wear", 0, MAX_WEAR);
    public FirstWallBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(WEAR, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(WEAR); }
}
