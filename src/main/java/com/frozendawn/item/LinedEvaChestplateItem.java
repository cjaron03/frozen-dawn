package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class LinedEvaChestplateItem extends ArmorItem {

    public LinedEvaChestplateItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.getOrDefault(ModDataComponents.CALORIC_RESISTANCE.get(), false)) {
            tooltip.add(Component.literal("Caldera retrofit installed.")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("Suppresses rupture-caldera overheat.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
