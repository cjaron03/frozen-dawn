package com.frozendawn.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;

/** Client-loaded roof detection; server-only NO_LEAVES heightmaps are not reliable here. */
final class PhaseTwoWindCover {
    private PhaseTwoWindCover() { }

    static boolean isExposed(BlockGetter blocks, BlockPos eye, int motionBlockingTop) {
        var cursor = eye.mutable();
        int top = Math.min(motionBlockingTop, blocks.getMaxBuildHeight());
        for (int y = Math.max(eye.getY(), blocks.getMinBuildHeight()); y < top; y++) {
            cursor.setY(y);
            var state = blocks.getBlockState(cursor);
            if (!(state.getBlock() instanceof LeavesBlock)
                    && (state.blocksMotion() || !state.getFluidState().isEmpty())) return false;
        }
        return true;
    }
}
