package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class OrsaThermalVisorItem extends ArmorItem {

    public OrsaThermalVisorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_thermal_visor")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_thermal_visor.use")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_thermal_visor.o2")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_thermal_visor.mode")
                .withStyle(ChatFormatting.YELLOW));
    }
}
