package com.frozendawn.item;

import com.frozendawn.init.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Issued only on death; its persistent player lease owns oxygen and thermal life support. */
public final class EmergencyEvaArmorItem extends ArmorItem {
    public EmergencyEvaArmorItem(Type type) {
        super(ModArmorMaterials.EMERGENCY_EVA, type,
                new Properties().durability(type.getDurability(5)));
    }
    @Override
    public boolean isValidRepairItem(ItemStack armor, ItemStack ingredient) { return false; }
    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.service").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.disposable").withStyle(ChatFormatting.GRAY));
    }
}
