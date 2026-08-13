package com.frozendawn.block;

import com.frozendawn.data.RemnantLureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RemnantMembraneBlock extends Block {
    public RemnantMembraneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) return;
        boolean active = RemnantLureSavedData.get(level.getServer()).at(pos)
                .map(record -> record.state().isCommitted()).orElse(false);
        if (active) level.scheduleTick(pos, this, 20 * 15);
        else level.removeBlock(pos, false);
    }
}
