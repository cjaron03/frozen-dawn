package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class RemnantEmberItem extends Item {

    public static final int MAX_WARMTH = 36000; // 30 minutes

    public RemnantEmberItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        // Only tick down when in offhand slot (slot 40)
        if (slotId != 40) return;

        int remaining = stack.getOrDefault(ModDataComponents.WARMTH_REMAINING.get(), 0);
        if (remaining <= 0) {
            stack.shrink(1);
            if (entity instanceof Player player) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.remnant_ember.burned_out"), true);
            }
            return;
        }
        stack.set(ModDataComponents.WARMTH_REMAINING.get(), remaining - 1);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int remaining = stack.getOrDefault(ModDataComponents.WARMTH_REMAINING.get(), 0);
        int totalSeconds = remaining / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        tooltipComponents.add(Component.translatable("tooltip.frozendawn.remnant_ember.warmth", minutes, seconds)
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.frozendawn.remnant_ember")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int remaining = stack.getOrDefault(ModDataComponents.WARMTH_REMAINING.get(), 0);
        return remaining < MAX_WARMTH;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int remaining = stack.getOrDefault(ModDataComponents.WARMTH_REMAINING.get(), 0);
        return Math.round(13.0f * remaining / MAX_WARMTH);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int remaining = stack.getOrDefault(ModDataComponents.WARMTH_REMAINING.get(), 0);
        float ratio = (float) remaining / MAX_WARMTH;
        // Orange gradient: from bright orange (full) to dark red (empty)
        int r = (int) Mth.lerp(1.0f - ratio, 180, 255);
        int g = (int) Mth.lerp(1.0f - ratio, 40, 160);
        int b = 20;
        return (r << 16) | (g << 8) | b;
    }
}
