package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ContainerTheme {

    private ContainerTheme() {
    }

    @SubscribeEvent
    public static void onContainerBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics graphics = event.getGuiGraphics();

        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int width = screen.getXSize();
        int height = screen.getYSize();
        int right = left + width;
        int bottom = top + height;

        graphics.fill(left, top, right, bottom, 0x4A0B1522);
        graphics.fillGradient(left, top, right, top + Math.max(18, height / 3), 0x583C6A94, 0x00000000);
        graphics.fillGradient(left, top + height / 2, right, bottom, 0x00000000, 0x4A081018);

        int outerBorder = 0xD0A9D9F7;
        int innerBorder = 0x905680A7;
        graphics.fill(left, top, right, top + 1, outerBorder);
        graphics.fill(left, bottom - 1, right, bottom, outerBorder);
        graphics.fill(left, top, left + 1, bottom, outerBorder);
        graphics.fill(right - 1, top, right, bottom, outerBorder);

        graphics.fill(left + 1, top + 1, right - 1, top + 2, innerBorder);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, innerBorder);
        graphics.fill(left + 1, top + 1, left + 2, bottom - 1, innerBorder);
        graphics.fill(right - 2, top + 1, right - 1, bottom - 1, innerBorder);

        renderHeaderGlow(graphics, left, top, width);
        renderPanelSnow(graphics, left, top, width, height);
        renderCornerRime(graphics, left, top, right, bottom);
    }

    private static void renderHeaderGlow(GuiGraphics graphics, int left, int top, int width) {
        int glowLeft = left + 6;
        int glowRight = left + width - 6;
        graphics.fillGradient(glowLeft, top + 4, glowRight, top + 22, 0x44CBEFFF, 0x00000000);
        graphics.fill(glowLeft + 6, top + 20, glowRight - 6, top + 21, 0x56B7E6FF);
    }

    private static void renderPanelSnow(GuiGraphics graphics, int left, int top, int width, int height) {
        int count = Mth.clamp((width * height) / 1500, 14, 42);
        for (int i = 0; i < count; i++) {
            float fx = hash01(i * 31 + width * 7 + height * 13);
            float fy = hash01(i * 19 + width * 11 + height * 5);
            int x = left + 6 + (int) (fx * Math.max(1, width - 12));
            int yBand = (i % 2 == 0) ? 28 : Math.max(34, height - 22);
            int y = top + Math.min(height - 10, yBand + (int) (fy * 12.0f));
            int size = (i % 5 == 0) ? 2 : 1;
            int color = (size == 2) ? 0x66EAF7FF : 0x4CC9E9FF;
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private static void renderCornerRime(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int frost = 0x86D8F3FF;
        int deepFrost = 0x4A8AB5D4;

        graphics.fill(left + 2, top + 2, left + 18, top + 3, frost);
        graphics.fill(left + 2, top + 3, left + 6, top + 7, deepFrost);
        graphics.fill(left + 10, top + 3, left + 12, top + 9, deepFrost);
        graphics.fill(right - 18, top + 2, right - 2, top + 3, frost);
        graphics.fill(right - 6, top + 3, right - 2, top + 8, deepFrost);
        graphics.fill(right - 12, top + 3, right - 10, top + 10, deepFrost);

        graphics.fill(left + 2, bottom - 3, left + 18, bottom - 2, deepFrost);
        graphics.fill(right - 18, bottom - 3, right - 2, bottom - 2, deepFrost);
    }

    private static float hash01(int seed) {
        int x = seed * 1664525 + 1013904223;
        x ^= x >>> 16;
        return (x & 0x00FFFFFF) / (float) 0x01000000;
    }
}
