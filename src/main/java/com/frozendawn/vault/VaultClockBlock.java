package com.frozendawn.vault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A block of the wall over the last door, carrying the backward clock on its west face. */
public final class VaultClockBlock extends BaseEntityBlock {
    public static final MapCodec<VaultClockBlock> CODEC = simpleCodec(VaultClockBlock::new);
    public VaultClockBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VaultClockBlockEntity(pos, state); }
}
