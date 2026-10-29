package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Icarus Matter Injector, the nozzle under the core that feeds ordinary matter upward. */
public final class IcarusInjectorBlock extends IcarusPartBlock implements EntityBlock {
    public IcarusInjectorBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IcarusInjectorBlockEntity(p, s); }
}
