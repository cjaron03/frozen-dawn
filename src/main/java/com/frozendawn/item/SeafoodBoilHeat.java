package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Steaming state shared by the Seafood Boil pot and its bowls. A running boil
 * stores the game time it goes cold; a paused one (fresh from cooking, or in a
 * Thermal Container) stores the ticks it has left until it is carried again.
 */
public final class SeafoodBoilHeat {

    /** Fresh from cooking, a boil steams this long once it is out of the furnace. */
    public static final int STEAM_TICKS = 20 * 60 * 5;
    public static final int WARM_MEAL_TICKS = 20 * 60 * 3;
    public static final float WARM_MEAL_BONUS = 15.0f;

    private SeafoodBoilHeat() {
    }

    public static boolean isSteaming(ItemStack stack, long now) {
        return stack.has(ModDataComponents.STEAM_HELD) || now < stack.getOrDefault(ModDataComponents.STEAMING_UNTIL, 0L);
    }

    /** Ticks of steam left, whether running or paused. */
    public static int remaining(ItemStack stack, long now) {
        Integer held = stack.get(ModDataComponents.STEAM_HELD);
        if (held != null) return held;
        return (int) Math.max(0L, stack.getOrDefault(ModDataComponents.STEAMING_UNTIL, 0L) - now);
    }

    /** Starts the countdown on a paused boil; a cold one loses its stale timestamp. */
    public static void release(ItemStack stack, long now) {
        Integer held = stack.get(ModDataComponents.STEAM_HELD);
        if (held != null) {
            stack.remove(ModDataComponents.STEAM_HELD);
            stack.set(ModDataComponents.STEAMING_UNTIL, now + held);
        } else if (stack.has(ModDataComponents.STEAMING_UNTIL) && now >= stack.get(ModDataComponents.STEAMING_UNTIL)) {
            stack.remove(ModDataComponents.STEAMING_UNTIL);
        }
    }

    /** Pauses a running boil while a Thermal Container holds it. */
    public static void hold(ItemStack stack, long now) {
        Long until = stack.get(ModDataComponents.STEAMING_UNTIL);
        if (until == null) return;
        stack.remove(ModDataComponents.STEAMING_UNTIL);
        if (until > now) stack.set(ModDataComponents.STEAM_HELD, (int) (until - now));
    }

    public static void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> lines) {
        long now = context.level() == null ? 0L : context.level().getGameTime();
        int ticks = remaining(stack, now);
        if (ticks > 0) {
            int seconds = ticks / 20;
            lines.add(Component.translatable("tooltip.frozendawn.seafood_boil.steaming", seconds / 60, String.format("%02d", seconds % 60))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("tooltip.frozendawn.seafood_boil.cold").withStyle(ChatFormatting.GRAY));
        }
    }
}
