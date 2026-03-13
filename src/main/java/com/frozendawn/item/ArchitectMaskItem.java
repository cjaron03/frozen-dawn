package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ArchitectMaskItem extends StandingAndWallBlockItem {

    public ArchitectMaskItem(Block block, Block wallBlock, Properties properties) {
        super(block, wallBlock, properties, net.minecraft.core.Direction.DOWN);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.architect_mask")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.architect_mask.flavor")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
