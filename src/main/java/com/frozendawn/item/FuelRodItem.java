package com.frozendawn.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Durability is remaining life in full-output seconds; never repaired or enchanted. */
public final class FuelRodItem extends Item {
    public FuelRodItem(Properties p) { super(p); }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack repair) { return false; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int life = 100 - 100 * stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
        tooltip.add(Component.translatable("item.frozendawn.fuel_rod.life", life).withStyle(ChatFormatting.GRAY));
    }
}
