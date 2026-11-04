package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The foot of the scanner's panel: a block of the Morgue's east wall that runs the room and carries the two-block display. */
public final class VaultVitalsBlock extends BaseEntityBlock {
    public static final MapCodec<VaultVitalsBlock> CODEC = simpleCodec(VaultVitalsBlock::new);
    public VaultVitalsBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VaultVitalsBlockEntity(pos, state); }
    @Override public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.VAULT_VITALS.get(), VaultVitalsBlockEntity::serverTick);
    }
}
