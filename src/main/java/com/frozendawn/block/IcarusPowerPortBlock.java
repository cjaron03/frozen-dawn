package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The Icarus power port: startup power in, net output to the grid. */
public final class IcarusPowerPortBlock extends IcarusPartBlock implements EntityBlock {
    public IcarusPowerPortBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IcarusPowerPortBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide() || t != ModBlockEntities.ICARUS_POWER_PORT.get() ? null : (w, pos, state, be) -> ((IcarusPowerPortBlockEntity) be).serverTick();
    }
}
