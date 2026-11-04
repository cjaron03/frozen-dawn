package com.frozendawn.vault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The west foot of the ORSA System Plot: a block of the wall that carries all 24 by 8 blocks of its lamps. */
public final class VaultPlotBlock extends BaseEntityBlock {
    public static final MapCodec<VaultPlotBlock> CODEC = simpleCodec(VaultPlotBlock::new);
    public VaultPlotBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VaultPlotBlockEntity(pos, state); }
}
