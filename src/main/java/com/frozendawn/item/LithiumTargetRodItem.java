package com.frozendawn.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A fuel-rod-shaped lithium target bred in a CFU-7 rod slot. Durability counts full-output seconds, like a fuel rod;
 * when it runs out the CFU-7 turns it into Tritium Canisters. Never repaired or enchanted. */
public final class LithiumTargetRodItem extends Item {
    public LithiumTargetRodItem(Properties p) { super(p); }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack repair) { return false; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int bred = 100 * stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
        tooltip.add(Component.translatable("item.frozendawn.lithium_target_rod.bred", bred).withStyle(ChatFormatting.GRAY));
    }
}
