package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The Halsey Core at the centre of the AMR-1 Icarus. Its sphere and shield are rendered, not placed. */
public final class HalseyCoreBlock extends IcarusPartBlock implements EntityBlock {
    public HalseyCoreBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HalseyCoreBlockEntity(p, s); }
    /** Client only: the core's animation and running sounds. The controller does all the server work. */
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (!l.isClientSide() || t != ModBlockEntities.HALSEY_CORE.get()) return null;
        return (w, pos, state, be) -> { com.frozendawn.client.IcarusCoreAnim.tick((HalseyCoreBlockEntity) be); com.frozendawn.client.IcarusLoopSound.tick((HalseyCoreBlockEntity) be); };
    }
}
