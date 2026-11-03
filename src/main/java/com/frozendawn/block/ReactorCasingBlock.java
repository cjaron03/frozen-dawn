package com.frozendawn.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** CFU-7 shell plate. A meltdown scorches it; a scorched casing still counts as shell. */
public final class ReactorCasingBlock extends Block {
    public static final BooleanProperty SCORCHED = BooleanProperty.create("scorched");
    public ReactorCasingBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(SCORCHED, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(SCORCHED); }
}
