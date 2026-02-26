package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class AcheroniteShardItem extends Item {

    public AcheroniteShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u26A0 Surface temperature: -189\u00B0C")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("  Exercise extreme caution when handling.")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }
}
