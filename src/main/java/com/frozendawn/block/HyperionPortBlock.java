package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The Hyperion's power network endpoint; each port carries up to 500 kW each way. */
public final class HyperionPortBlock extends HyperionFacingPartBlock implements EntityBlock {
    public HyperionPortBlock(Properties p) {
        super(p.lightLevel(s -> s.getValue(HyperionControllerBlock.ALERT) ? 6 : 0));
        registerDefaultState(defaultBlockState().setValue(HyperionControllerBlock.ALERT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(HyperionControllerBlock.ALERT); }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new HyperionPortBlockEntity(p, s); }
}
