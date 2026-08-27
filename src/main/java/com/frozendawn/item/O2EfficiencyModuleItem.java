package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Passive ORSA scrubber module. Carrying one reduces all suit O2 consumption. */
public final class O2EfficiencyModuleItem extends Item {
    public static final double CONSUMPTION_MULTIPLIER = 0.75D;

    public O2EfficiencyModuleItem(Properties properties) {
        super(properties);
    }

    public static boolean isInstalled(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem()
                    instanceof O2EfficiencyModuleItem) {
                return true;
            }
        }
        return false;
    }

    public static double consumptionMultiplier(Player player) {
        return isInstalled(player) ? CONSUMPTION_MULTIPLIER : 1.0D;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.o2_efficiency_module")
                .withStyle(ChatFormatting.AQUA));
    }
}
