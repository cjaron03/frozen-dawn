package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.MasterArchitectBossBarPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/** Draws the synchronized Master Architect boss event in its own blue-black palette. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectBossBarRenderer {
    private static final int BAR_WIDTH = 182;
    private static final int INNER_WIDTH = BAR_WIDTH - 2;
    private static final int BORDER_COLOR = 0xFF010409;
    private static final int EMPTY_COLOR = 0xFF07121B;
    private static final int PROGRESS_COLOR = 0xFF0A3040;
    private static final int PROGRESS_HIGHLIGHT = 0xFF176478;
    private static final int NAME_COLOR = 0xFFB8D8DE;

    private MasterArchitectBossBarRenderer() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!(event.getBossEvent().getName().getContents()
                instanceof TranslatableContents contents)
                || !MasterArchitectBossBarPolicy.NAME_KEY.equals(contents.getKey())) {
            return;
        }

        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getX();
        int y = event.getY();
        graphics.fill(x, y, x + BAR_WIDTH, y + 5, BORDER_COLOR);
        graphics.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + 4, EMPTY_COLOR);

        int progressWidth = Mth.clamp(
                Math.round(event.getBossEvent().getProgress() * INNER_WIDTH),
                0,
                INNER_WIDTH);
        if (progressWidth > 0) {
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + 1 + progressWidth,
                    y + 4,
                    PROGRESS_COLOR);
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + 1 + progressWidth,
                    y + 2,
                    PROGRESS_HIGHLIGHT);
        }

        Minecraft minecraft = Minecraft.getInstance();
        int nameX = graphics.guiWidth() / 2
                - minecraft.font.width(event.getBossEvent().getName()) / 2;
        graphics.drawString(
                minecraft.font,
                event.getBossEvent().getName(),
                nameX,
                y - 9,
                NAME_COLOR);
    }
}
