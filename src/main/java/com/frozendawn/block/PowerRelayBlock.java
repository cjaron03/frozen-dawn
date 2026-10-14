package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Passive link node. Pylons do not seal rooms; the feedthrough is a full airtight block. */
public final class PowerRelayBlock extends Block {
    private final int range;
    private final boolean feedthrough;
    private final VoxelShape shape;
    public PowerRelayBlock(Properties properties, int range, boolean feedthrough) {
        super(properties); this.range = range; this.feedthrough = feedthrough;
        shape = feedthrough ? Shapes.block() : Shapes.or(box(2, 0, 2, 14, 3, 14),
                box(6, 3, 6, 10, 13, 10), box(4, 13, 4, 12, 16, 12));
    }
    public int linkRange() { return range; }
    public boolean isFeedthrough() { return feedthrough; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
