package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HyperionPortBlockEntity extends BlockEntity {
    public HyperionPortBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HYPERION_PORT.get(), p, s); }
}
