package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Any SMS-1 Hyperion block. Pistons never move one, and placing or removing one tells nearby controllers. */
public class HyperionPartBlock extends Block {
    public HyperionPartBlock(Properties p) { super(p); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(state.getBlock())) HyperionControllerBlockEntity.partChanged(level, pos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        boolean changed = !state.is(next.getBlock());
        super.onRemove(state, level, pos, next, moved);
        if (changed) HyperionControllerBlockEntity.partChanged(level, pos);
    }
}
