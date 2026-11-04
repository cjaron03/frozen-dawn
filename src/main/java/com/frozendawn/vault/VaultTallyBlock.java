package com.frozendawn.vault;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A block with a lamp in it. Over the east gate, one of the row Argus puts out for every right fuse; over a puzzle's door, lit when it
 * opens; underfoot, the Morgue's scanner plate, lit while it reads someone. */
public final class VaultTallyBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public VaultTallyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIT); }
    public static int light(BlockState state) { return state.getValue(LIT) ? 7 : 0; }
}
