package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Hyperion's brain and charge store; its window faces outward. */
public final class HyperionControllerBlock extends HyperionFacingPartBlock implements EntityBlock {
    public HyperionControllerBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HyperionControllerBlockEntity(p, s); }
}
