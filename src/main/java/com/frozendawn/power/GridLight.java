package com.frozendawn.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A light funded by the ordinary one-second grid allocation. */
public interface GridLight {
    int demandWatts(BlockState state);
    void setGridPowered(ServerLevel level, BlockPos pos, boolean powered);
}
