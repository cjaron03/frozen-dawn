package com.frozendawn.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The lamp strip out of unit nine. It never lights, and it is not lost: she asked for it. */
public final class NineLampItem extends Item {
    public NineLampItem(Properties properties) {
        super(properties);
    }

    @Override public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.nine_lamp.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
