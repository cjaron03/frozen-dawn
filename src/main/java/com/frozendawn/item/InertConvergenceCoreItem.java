package com.frozendawn.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class InertConvergenceCoreItem extends BlockItem {
    public static final int MAX_USES = 3;

    public InertConvergenceCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int remainingUses(ItemStack stack) {
        return Math.clamp(MAX_USES - stack.getDamageValue(), 0, MAX_USES);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "tooltip.frozendawn.inert_convergence_core.uses",
                        remainingUses(stack), MAX_USES)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "tooltip.frozendawn.inert_convergence_core.tool")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
