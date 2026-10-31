package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The Halsey Core at the centre of the AMR-1 Icarus. Its sphere and shield are rendered, not placed. While the plasma
 * holds the core (IcarusLook.coreHidden) the block is HIDDEN: it draws nothing and lights its chamber. */
public final class HalseyCoreBlock extends IcarusPartBlock implements EntityBlock {
    public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");
    public HalseyCoreBlock(Properties p) { super(p.lightLevel(s -> s.getValue(HIDDEN) ? 15 : 0)); registerDefaultState(stateDefinition.any().setValue(HIDDEN, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HIDDEN); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HalseyCoreBlockEntity(p, s); }
    /** Client only: the core's animation and running sounds. The controller does all the server work. */
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (!l.isClientSide() || t != ModBlockEntities.HALSEY_CORE.get()) return null;
        return (w, pos, state, be) -> { com.frozendawn.client.IcarusCoreAnim.tick((HalseyCoreBlockEntity) be); com.frozendawn.client.IcarusLoopSound.tick((HalseyCoreBlockEntity) be); };
    }
}
