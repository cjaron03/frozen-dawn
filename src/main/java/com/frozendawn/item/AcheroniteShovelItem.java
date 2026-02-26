package com.frozendawn.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Acheronite Shovel: clears snow layers and snow blocks in a 3x3 area.
 */
public class AcheroniteShovelItem extends ShovelItem {

    public AcheroniteShovelItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        boolean result = super.mineBlock(stack, level, state, pos, miner);

        if (!level.isClientSide() && isSnow(state)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos neighbor = pos.offset(dx, 0, dz);
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (isSnow(neighborState)) {
                        level.destroyBlock(neighbor, true, miner);
                    }
                }
            }
        }

        return result;
    }

    private static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }
}
