package com.frozendawn.block;

import com.frozendawn.aggregate.AggregateSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
            AggregateSavedData.get(serverLevel.getServer()).setStillpoint(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            AggregateSavedData.get(serverLevel.getServer()).clearStillpoint(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
