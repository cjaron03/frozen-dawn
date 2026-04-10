package com.frozendawn.item;

import com.frozendawn.compat.curios.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CuriosAccessoryTooltip {

    private CuriosAccessoryTooltip() {}

    public static void appendRequirement(List<Component> tooltip) {
        if (!CuriosCompat.isLoaded()) {
            tooltip.add(Component.translatable("tooltip.frozendawn.curios_required")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
