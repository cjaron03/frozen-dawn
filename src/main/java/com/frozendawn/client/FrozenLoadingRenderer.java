package com.frozendawn.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class FrozenLoadingRenderer {

    private static final ResourceLocation MOJANG_LOGO = ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");

    private FrozenLoadingRenderer() {
    }

    public static void render(GuiGraphics graphics, int width, int height, float progressBias, float overlayAlpha, float progressBarAlpha) {
        int baseColor = applyAlpha(StartupFreezeVisuals.loadingBackground(progressBias), overlayAlpha);
        int glowColor = FastColor.ARGB32.color((int) (overlayAlpha * 92.0f), 0x90, 0xD4, 0xFF);
        int shadeColor = FastColor.ARGB32.color((int) (overlayAlpha * 96.0f), 0x05, 0x0B, 0x14);

        graphics.fill(0, 0, width, height, baseColor);
        graphics.fillGradient(0, 0, width, height / 2, glowColor, 0x00000000);
        graphics.fillGradient(0, height / 2, width, height, 0x00000000, shadeColor);

        int centerX = width / 2;
        int centerY = height / 2;
        double logoHeight = Math.min(width * 0.75, height) * 0.25;
        int halfLogoHeight = (int) (logoHeight * 0.5);
        int halfLogoWidth = (int) (logoHeight * 2.0);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        graphics.setColor(1.0F, 1.0F, 1.0F, overlayAlpha);
        graphics.blit(MOJANG_LOGO, centerX - halfLogoWidth, centerY - halfLogoHeight, halfLogoWidth, (int) logoHeight, -0.0625F, 0.0F, 120, 60, 120, 120);
        graphics.blit(MOJANG_LOGO, centerX, centerY - halfLogoHeight, halfLogoWidth, (int) logoHeight, 0.0625F, 60.0F, 120, 60, 120, 120);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (progressBarAlpha <= 0.0f) {
            return;
        }

        int barWidth = halfLogoWidth * 2;
        int barLeft = centerX - halfLogoWidth;
        int barTop = (int) (height * 0.8325) - 5;
        int barBottom = barTop + 10;
        int borderColor = FastColor.ARGB32.color((int) (progressBarAlpha * 220.0f), 0xD7, 0xF4, 0xFF);
        int fillColor = FastColor.ARGB32.color((int) (progressBarAlpha * 255.0f), 0xB2, 0xE4, 0xFF);
        int fillShadowColor = FastColor.ARGB32.color((int) (progressBarAlpha * 160.0f), 0x54, 0x88, 0xB6);

        graphics.fill(barLeft, barTop, barLeft + barWidth, barBottom, FastColor.ARGB32.color((int) (progressBarAlpha * 110.0f), 0x08, 0x11, 0x1B));
        int innerWidth = Math.max(0, Mth.ceil((barWidth - 4) * Mth.clamp(progressBias, 0.0f, 1.0f)));
        if (innerWidth > 0) {
            graphics.fill(barLeft + 2, barTop + 2, barLeft + 2 + innerWidth, barBottom - 2, fillShadowColor);
            graphics.fillGradient(barLeft + 2, barTop + 2, barLeft + 2 + innerWidth, barBottom - 2, fillColor, fillShadowColor);
        }

        graphics.fill(barLeft + 1, barTop, barLeft + barWidth - 1, barTop + 1, borderColor);
        graphics.fill(barLeft + 1, barBottom - 1, barLeft + barWidth - 1, barBottom, borderColor);
        graphics.fill(barLeft, barTop, barLeft + 1, barBottom, borderColor);
        graphics.fill(barLeft + barWidth - 1, barTop, barLeft + barWidth, barBottom, borderColor);

        renderIcicles(graphics, barLeft, barBottom, barWidth, StartupFreezeVisuals.icicleProgress(StartupFreezeVisuals.tint(progressBias)), progressBarAlpha);
    }

    private static void renderIcicles(GuiGraphics graphics, int barLeft, int barBottom, int barWidth, float progress, float alpha) {
        if (progress <= 0.0f || alpha <= 0.0f) {
            return;
        }

        int iceColor = FastColor.ARGB32.color((int) (alpha * 200.0f), 0xA0, 0xD0, 0xF0);
        int iceShadowColor = FastColor.ARGB32.color((int) (alpha * 180.0f), 0x60, 0x90, 0xC0);
        int frostColor = FastColor.ARGB32.color((int) (alpha * 140.0f), 0xC8, 0xED, 0xFF);
        graphics.fill(barLeft - 1, barBottom, barLeft + barWidth + 1, barBottom + 1, frostColor);

        int icicleTop = barBottom + 1;
        int count = Math.max(12, barWidth / 28);
        for (int i = 0; i < count; i++) {
            float frac = (float) i / (count - 1);
            int x = barLeft + Math.round(barWidth * frac);
            float profile = 1.0f - Math.abs(frac - 0.5f) * 1.35f;
            int maxLen = Math.max(8, Math.round(18.0f * profile));
            int len = Math.round(maxLen * progress);
            if (len < 3) {
                continue;
            }

            int baseWidth = i % 3 == 0 ? 3 : 2;
            int bodyLen = len * 2 / 3;
            graphics.fill(x, icicleTop, x + baseWidth, icicleTop + bodyLen, iceColor);
            graphics.fill(x, icicleTop + bodyLen, x + 1, icicleTop + len, iceShadowColor);
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Mth.clamp(Math.round(((color >>> 24) & 0xFF) * alpha), 0, 255);
        return color & 0x00FFFFFF | a << 24;
    }
}
