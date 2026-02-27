package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class O2TankItem extends Item {

    public static final int TIER1_MAX = 1200;  // 60 seconds
    public static final int TIER2_MAX = 2400;  // 120 seconds
    public static final int TIER3_MAX = 3600;  // 180 seconds

    private final int maxO2;

    public O2TankItem(Properties properties, int maxO2) {
        super(properties);
        this.maxO2 = maxO2;
    }

    public int getMaxO2() {
        return maxO2;
    }

    /** Returns 1, 2, or 3 based on capacity. */
    public int getTier() {
        if (maxO2 >= TIER3_MAX) return 3;
        if (maxO2 >= TIER2_MAX) return 2;
        return 1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int o2 = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), maxO2);
        return o2 < maxO2;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int o2 = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), maxO2);
        return Math.round(13.0f * o2 / maxO2);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int o2 = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), maxO2);
        float ratio = (float) o2 / maxO2;
        return switch (getTier()) {
            case 3 -> {
                // Purple/violet → dim when empty
                int r = (int) Mth.lerp(ratio, 80, 160);
                int g = (int) Mth.lerp(ratio, 40, 60);
                int b = (int) Mth.lerp(ratio, 80, 220);
                yield (r << 16) | (g << 8) | b;
            }
            case 2 -> {
                // Blue → dim when empty
                int r = (int) Mth.lerp(ratio, 60, 40);
                int g = (int) Mth.lerp(ratio, 60, 120);
                int b = (int) Mth.lerp(ratio, 80, 230);
                yield (r << 16) | (g << 8) | b;
            }
            default -> {
                // Cyan → red when empty
                int r = (int) Mth.lerp(ratio, 255, 0);
                int g = (int) Mth.lerp(ratio, 80, 204);
                int b = (int) Mth.lerp(ratio, 80, 204);
                yield (r << 16) | (g << 8) | b;
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String tierName = switch (getTier()) {
            case 3 -> "Mk.III";
            case 2 -> "Mk.II";
            default -> "Mk.I";
        };
        ChatFormatting tierColor = switch (getTier()) {
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            case 2 -> ChatFormatting.BLUE;
            default -> ChatFormatting.AQUA;
        };

        int o2 = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), maxO2);
        int percent = Math.round(100f * o2 / maxO2);
        int seconds = o2 / 20;

        tooltip.add(Component.literal(tierName + " — O2: " + percent + "%").withStyle(tierColor));
        tooltip.add(Component.literal(seconds + "s of air remaining").withStyle(ChatFormatting.GRAY));
    }
}
