package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.power.IcarusPhysics;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A sealed ORSA antimatter capsule. Its charge is exact, in micro-capsules; outside a Halsey Core it is inert and safe to carry. */
public final class AntimatterCapsuleItem extends Item {
    public AntimatterCapsuleItem(Properties properties) { super(properties); }

    public static long charge(ItemStack stack) {
        return stack.is(ModItems.ANTIMATTER_CAPSULE) ? Math.clamp(stack.getOrDefault(ModDataComponents.ANTIMATTER.get(), 0L), 0, IcarusPhysics.CAPSULE) : 0;
    }
    public static ItemStack sealed(long charge) {
        var stack = new ItemStack(ModItems.ANTIMATTER_CAPSULE.get());
        stack.set(ModDataComponents.ANTIMATTER.get(), Math.clamp(charge, 0, IcarusPhysics.CAPSULE));
        return stack;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.antimatter_capsule.charge", String.format(java.util.Locale.ROOT, "%.1f", charge(stack) * 100.0 / IcarusPhysics.CAPSULE)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.frozendawn.antimatter_capsule.inert").withStyle(ChatFormatting.GRAY));
    }
}
