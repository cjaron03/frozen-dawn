package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/** Joins the SFR-1 to a power network: it draws the startup load and supplies the burn's net output. */
public final class SfrPowerPortBlock extends Block implements EntityBlock {
    public SfrPowerPortBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new SfrPowerPortBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return !l.isClientSide() && t == ModBlockEntities.SFR_POWER_PORT.get() ? (w, pos, state, be) -> ((SfrPowerPortBlockEntity) be).serverTick() : null;
    }
}
