package com.frozendawn.item;

import com.frozendawn.init.ModBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Acheronite Pickaxe: mines frozen blocks 2x faster.
 */
public class AcheronitePickaxeItem extends PickaxeItem {

    public AcheronitePickaxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = super.getDestroySpeed(stack, state);
        if (isFrozenBlock(state)) {
            return base * 2.0F;
        }
        return base;
    }

    private static boolean isFrozenBlock(BlockState state) {
        return state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())
                || state.is(ModBlocks.FROZEN_LOG.get())
                || state.is(ModBlocks.FROZEN_LEAVES.get())
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get())
                || state.is(ModBlocks.FROZEN_COAL_ORE.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                || state.is(net.minecraft.world.level.block.Blocks.ICE)
                || state.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                || state.is(net.minecraft.world.level.block.Blocks.BLUE_ICE);
    }
}
