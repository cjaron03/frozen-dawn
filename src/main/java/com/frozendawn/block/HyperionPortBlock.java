package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Hyperion's power network endpoint; each port carries up to 500 kW each way. */
public final class HyperionPortBlock extends HyperionFacingPartBlock implements EntityBlock {
    public HyperionPortBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HyperionPortBlockEntity(p, s); }
}
