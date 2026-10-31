package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Hyperion's brain and charge store; its window faces outward. */
public final class HyperionControllerBlock extends HyperionFacingPartBlock implements EntityBlock {
    public HyperionControllerBlock(Properties p) { super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HyperionControllerBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return !l.isClientSide() && t == ModBlockEntities.HYPERION_CONTROLLER.get()
                ? (w, pos, state, be) -> { if (w.getGameTime() % 20 == 0) ((HyperionControllerBlockEntity) be).second((ServerLevel) w); } : null;
    }
}
