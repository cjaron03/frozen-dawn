package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

public class VolcanicAshBlock extends SnowLayerBlock {

    public VolcanicAshBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof Level realLevel
                && FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(realLevel, pos)) {
            return false;
        }
        return super.canSurvive(state, level, pos);
    }
}
