package com.frozendawn.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The cooked pot. Carried, it cools; placed, it serves four bowls. */
public class SeafoodBoilItem extends BlockItem {

    public SeafoodBoilItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) SeafoodBoilHeat.release(stack, level.getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SeafoodBoilHeat.appendTooltip(stack, context, lines);
        lines.add(Component.translatable("tooltip.frozendawn.seafood_boil.serve").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
