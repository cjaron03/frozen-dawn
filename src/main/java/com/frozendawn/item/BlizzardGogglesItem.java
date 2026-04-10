package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class BlizzardGogglesItem extends Item {

    public BlizzardGogglesItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.blizzard_goggles")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.blizzard_goggles.use")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.blizzard_goggles.mode")
                .withStyle(ChatFormatting.BLUE));
        CuriosAccessoryTooltip.appendRequirement(tooltip);
    }
}
