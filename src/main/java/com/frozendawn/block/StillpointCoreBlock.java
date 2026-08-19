package com.frozendawn.block;

import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.aggregate.StillpointFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The single relocatable quiet point left by the Aggregate. */
public final class StillpointCoreBlock extends Block {
    public StillpointCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            AggregateSavedData.get(serverLevel.getServer()).armStillpoint(
                    serverLevel, pos, null);
            StillpointFieldManager.announceCharge(serverLevel, pos);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            AggregateSavedData.get(serverLevel.getServer()).armStillpoint(
                    serverLevel, pos, placer == null ? null : placer.getUUID());
            if (placer instanceof ServerPlayer player) {
                StillpointFieldManager.handleFirstPlacement(player);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            AggregateSavedData.get(serverLevel.getServer()).clearStillpoint(serverLevel, pos);
            StillpointFieldManager.syncAll(serverLevel.getServer());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
