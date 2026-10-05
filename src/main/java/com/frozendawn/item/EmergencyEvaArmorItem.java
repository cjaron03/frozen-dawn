package com.frozendawn.item;

import com.frozendawn.init.ModArmorMaterials;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.data.EmergencyEvaState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Unbreakable;

/** Issued only on death; its persistent player lease owns oxygen and thermal life support. */
public final class EmergencyEvaArmorItem extends ArmorItem {
    public EmergencyEvaArmorItem(Type type) {
        super(ModArmorMaterials.EMERGENCY_EVA, type,
                new Properties().durability(type.getDurability(5))
                        .component(DataComponents.UNBREAKABLE, new Unbreakable(false)));
    }
    @Override
    public boolean isValidRepairItem(ItemStack armor, ItemStack ingredient) { return false; }
    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) { return false; }
    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * serviceTicks(stack) / EmergencyEvaState.SERVICE_TICKS);
    }
    @Override
    public int getBarColor(ItemStack stack) {
        int ticks = serviceTicks(stack);
        return ticks <= 2400 ? 0xFFB1B1 : ticks <= 6000 ? 0xFFE0A8 : 0xCDEFFF;
    }
    public static int serviceTicks(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.EMERGENCY_EVA_SERVICE, 0),
                0, EmergencyEvaState.SERVICE_TICKS);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.service").withStyle(ChatFormatting.GOLD));
        int seconds = (serviceTicks(stack) + 19) / 20;
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.remaining",
                String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60))
                .withStyle(seconds > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.exertion").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_eva.disposable").withStyle(ChatFormatting.GRAY));
    }
}
