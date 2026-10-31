package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A multiblock cell that does not hold what the structure needs there, with the state its ghost shows; null for a cell
 * that must be cleared to air. Shared by the SFR-1 and the AMR-1 Icarus build guides. */
public record GhostCell(BlockPos pos, @Nullable BlockState ghost) {
    public @Nullable Block expected() { return ghost == null ? null : ghost.getBlock(); }
}
