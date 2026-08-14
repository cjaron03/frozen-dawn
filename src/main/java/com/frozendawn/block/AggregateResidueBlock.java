package com.frozendawn.block;

import com.frozendawn.aggregate.AggregateSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Short-lived residue from recent kills; authored Ossuary residue remains permanent. */
public final class AggregateResidueBlock extends Block {
    public AggregateResidueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            server.scheduleTick(pos, this, 1_200 + server.random.nextInt(2_401));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                        RandomSource random) {
        if (!AggregateSavedData.get(level.getServer()).ownsOssuaryBlock(pos)) {
            level.removeBlock(pos, false);
        }
    }
}
