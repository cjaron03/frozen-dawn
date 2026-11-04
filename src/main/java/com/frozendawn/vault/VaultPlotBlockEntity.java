package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it is here so {@code VaultPlotRenderer} can draw the plot at full brightness. */
public final class VaultPlotBlockEntity extends BlockEntity {
    public VaultPlotBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_PLOT.get(), pos, state); }
}
