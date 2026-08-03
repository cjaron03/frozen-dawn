package com.frozendawn.item;

import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Three memories of the player, each capable of denying one death. */
public final class LastWitnessItem extends Item {
    public static final int MAX_CHARGES = 3;

    public LastWitnessItem(Properties properties) {
        super(properties);
    }

    public static int remainingCharges(ItemStack stack) {
        return Mth.clamp(MAX_CHARGES - stack.getDamageValue(), 0, MAX_CHARGES);
    }

    public static boolean hasMemory(ItemStack stack) {
        return stack.is(ModItems.THE_LAST_WITNESS.get())
                && remainingCharges(stack) > 0;
    }

    public static ItemStack consumeMemory(ItemStack stack) {
        if (remainingCharges(stack) <= 1) {
            return new ItemStack(ModItems.MEMORY_NODE_EMPTY.get());
        }
        stack.setDamageValue(stack.getDamageValue() + 1);
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return remainingCharges(stack) >= 2;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.the_last_witness.effect")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                        "tooltip.frozendawn.the_last_witness.charges",
                        remainingCharges(stack), MAX_CHARGES)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.the_last_witness.flavor")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
