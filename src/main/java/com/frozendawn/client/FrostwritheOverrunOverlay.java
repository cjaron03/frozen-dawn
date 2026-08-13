package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwritheState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Brief edge pressure while the colony spreads around the player's legs. */
public final class FrostwritheOverrunOverlay {
    private FrostwritheOverrunOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!FrozenDawnConfig.ENABLE_FROST_OVERLAY.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.player.isCreative() || minecraft.player.isSpectator()) {
            return;
        }
        FrostwritheEntity colony = minecraft.level.getEntitiesOfClass(
                FrostwritheEntity.class,
                minecraft.player.getBoundingBox().inflate(2.75D),
                entity -> entity.activityState() == FrostwritheState.OVERRUN)
                .stream().findFirst().orElse(null);
        if (colony == null) return;

        float progress = Mth.clamp((colony.stateTicks()
                + deltaTracker.getGameTimeDeltaPartialTick(false)) / 30.0F,
                0.0F, 1.0F);
        float pulse = Mth.sin(progress * Mth.PI);
        int alpha = Mth.clamp(Math.round(88.0F * pulse), 0, 88);
        int edge = Math.max(20, graphics.guiHeight() / 8);
        int side = Math.max(24, graphics.guiWidth() / 12);
        int color = (alpha << 24) | 0xDDE7E5;
        int clear = 0x00DDE7E5;
        graphics.fillGradient(0, 0, graphics.guiWidth(), edge, color, clear);
        graphics.fillGradient(0, graphics.guiHeight() - edge,
                graphics.guiWidth(), graphics.guiHeight(), clear, color);
        graphics.fillGradient(0, 0, side, graphics.guiHeight(), color, clear);
        graphics.fillGradient(graphics.guiWidth() - side, 0,
                graphics.guiWidth(), graphics.guiHeight(), clear, color);
    }
}
