package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One deterministic structure edit plus its player-conduct meaning.
 */
public record HearthStructurePlacement(
        HearthStructurePiece piece,
        BlockPos offset,
        Direction facing,
        int variant,
        Protection protection) {

    public enum Protection {
        NONE,
        STRUCTURE,
        DOOR,
        CONTAINER,
        HEARTH_RING
    }
}
