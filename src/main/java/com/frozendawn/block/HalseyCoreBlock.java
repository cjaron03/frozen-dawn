package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Halsey Core at the centre of the AMR-1 Icarus. Its sphere and shield are rendered, not placed. */
public final class HalseyCoreBlock extends IcarusPartBlock implements EntityBlock {
    public HalseyCoreBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HalseyCoreBlockEntity(p, s); }
}
