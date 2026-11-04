package com.frozendawn.vault;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** One lamp of the row over the east gate. Argus puts one out for every right fuse. */
public final class VaultTallyBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public VaultTallyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    public static int light(BlockState state) { return state.getValue(LIT) ? 7 : 0; }
}
