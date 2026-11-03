package com.frozendawn.block;

import com.frozendawn.world.IcarusDetonation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/** Every AMR-1 Icarus part. Pistons cannot move one, and removing one by any means reports to IcarusDetonation, which
 * decides whether a charged core goes up. */
public abstract class IcarusPartBlock extends Block {
    protected IcarusPartBlock(Properties p) { super(p.pushReaction(PushReaction.BLOCK)); }

    /** Runs before super so a Controller's own block entity is still there when it is the part removed. */
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l instanceof ServerLevel server) IcarusDetonation.partRemoved(server, p);
        super.onRemove(s, l, p, next, moving);
    }

    /** A part with no state or behaviour of its own: the frame, the injector casing, the stabilizer housing and the reserve bank. */
    public static final class Simple extends IcarusPartBlock {
        public Simple(Properties p) { super(p); }
    }
}
