package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HyperionControllerBlockEntity extends BlockEntity {
    public HyperionControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HYPERION_CONTROLLER.get(), p, s); }
    public static void partChanged(Level level, BlockPos pos) {}
}
