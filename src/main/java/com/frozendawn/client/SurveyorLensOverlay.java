package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.SurveyorLensScanner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

public final class SurveyorLensOverlay {

    private static final ResourceLocation ORSA_LOGO =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/orsa_logo.png");
    private static final int HUD_WIDTH = 144;
    private static final int HUD_HEIGHT = 46;

    private SurveyorLensOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!SurveyorLensVision.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float strength = SurveyorLensVision.getOverlayStrength();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int tintAlpha = (int) (strength * 42.0F);
        graphics.fill(0, 0, width, height, (tintAlpha << 24) | 0x243444);

        int edgeAlpha = (int) (strength * 92.0F);
        int edgeColor = (edgeAlpha << 24) | 0x0F1B24;
        int transparent = 0x000F1B24;
        int borderSize = (int) (height * 0.12F);
        int sideBorder = (int) (width * 0.08F);

        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

        if (SurveyorLensVision.isThermalModeVisible()) {
            float thermalStrength = SurveyorLensVision.getThermalModeStrength();
            drawThermalWash(graphics, mc, width, height, thermalStrength);
            if (SurveyorLensVision.isThermalBooting()) {
                drawBootSequence(graphics, mc, width, height, thermalStrength);
            } else {
                drawThermalHud(graphics, mc, width, thermalStrength);
            }
        }
    }

    private static void drawThermalWash(GuiGraphics graphics, Minecraft mc, int width, int height, float thermalStrength) {
        int coldAlpha = (int) (Mth.clamp(thermalStrength, 0.15F, 1.0F) * 58.0F);
        graphics.fill(0, 0, width, height, (coldAlpha << 24) | 0x1A2A34);

        int hazeAlpha = (int) (thermalStrength * 36.0F);
        graphics.fill(0, 0, width, height, (hazeAlpha << 24) | 0x466978);

        int edgeAlpha = (int) (thermalStrength * 120.0F);
        int edgeColor = (edgeAlpha << 24) | 0x0A1118;
        int transparent = 0x000A1118;
        int borderSize = (int) (height * 0.15F);
        int sideBorder = (int) (width * 0.09F);
        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        int scanAlphaBase = (int) (thermalStrength * 28.0F);
        for (int y = 0; y < height; y += 3) {
            int lineAlpha = scanAlphaBase + (((y + (int) (time * 2L)) / 3) % 3 == 0 ? 6 : 0);
            graphics.fill(0, y, width, y + 1, (lineAlpha << 24) | 0x6B8A98);
        }

        int frameAlpha = (int) (thermalStrength * 170.0F);
        int frameColor = (frameAlpha << 24) | 0x92D4E2;
        int frameTransparent = 0x0092D4E2;
        int inset = 10;
        graphics.fillGradient(inset, inset, inset + 22, inset + 2, frameColor, frameTransparent);
        graphics.fillGradient(inset, inset, inset + 2, inset + 22, frameColor, frameTransparent);
        graphics.fillGradient(width - inset - 22, inset, width - inset, inset + 2, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 2, inset, width - inset, inset + 22, frameColor, frameTransparent);
        graphics.fillGradient(inset, height - inset - 2, inset + 22, height - inset, frameColor, frameTransparent);
        graphics.fillGradient(inset, height - inset - 22, inset + 2, height - inset, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 22, height - inset - 2, width - inset, height - inset, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 2, height - inset - 22, width - inset, height - inset, frameTransparent, frameColor);
    }

    private static void drawThermalHud(GuiGraphics graphics, Minecraft mc, int width, float thermalStrength) {
        int panelX = width - HUD_WIDTH - 10;
        int panelY = 10;
        int bgAlpha = (int) (Mth.clamp(thermalStrength + 0.2F, 0.25F, 1.0F) * 150.0F);
        int borderAlpha = (int) (Mth.clamp(thermalStrength + 0.15F, 0.2F, 1.0F) * 215.0F);

        graphics.fill(panelX, panelY, panelX + HUD_WIDTH, panelY + HUD_HEIGHT, (bgAlpha << 24) | 0x091116);
        graphics.fill(panelX, panelY, panelX + HUD_WIDTH, panelY + 1, (borderAlpha << 24) | 0x93D8E8);
        graphics.fill(panelX, panelY + HUD_HEIGHT - 1, panelX + HUD_WIDTH, panelY + HUD_HEIGHT, (borderAlpha << 24) | 0x355B66);
        graphics.fill(panelX, panelY, panelX + 1, panelY + HUD_HEIGHT, (borderAlpha << 24) | 0x93D8E8);
        graphics.fill(panelX + HUD_WIDTH - 1, panelY, panelX + HUD_WIDTH, panelY + HUD_HEIGHT, (borderAlpha << 24) | 0x355B66);

        graphics.blit(ORSA_LOGO, panelX + 7, panelY + 7, 0, 0, 16, 16, 16, 16);
        graphics.drawString(mc.font, "THERMAL ARRAY", panelX + 30, panelY + 7, 0xDFFBFFFF, false);

        List<SurveyorLensScanner.HeatSignature> signatures = SurveyorLensVision.getCachedSignatures();
        String status = SurveyorLensVision.isThermalBooting() ? "INITIALIZING" : "ONLINE";
        String sigText = String.format(Locale.ROOT, "%s  //  %02d SIG", status, signatures.size());
        graphics.drawString(mc.font, fit(mc, sigText, 104), panelX + 30, panelY + 17, 0x88DFF4, false);

        if (!signatures.isEmpty()) {
            SurveyorLensScanner.HeatSignature primary = signatures.getFirst();
            String sourceName = fit(mc, primary.sourceType().displayName().getString().toUpperCase(Locale.ROOT), 128);
            String direction = fit(mc, primary.direction().getString().toUpperCase(Locale.ROOT), 86);
            graphics.drawString(mc.font, sourceName, panelX + 7, panelY + 29, 0xFFF2C567, false);
            graphics.drawString(mc.font, direction + " // " + primary.distanceBlocks() + "M", panelX + 7, panelY + 38, 0x9DC8D6, false);
        } else {
            graphics.drawString(mc.font, "NO THERMAL ANCHORS", panelX + 7, panelY + 31, 0x6E8A94, false);
        }
    }

    private static void drawBootSequence(GuiGraphics graphics, Minecraft mc, int width, int height, float thermalStrength) {
        float progress = SurveyorLensVision.getThermalBootProgress();
        float transfer = Mth.clamp((progress - 0.68F) / 0.32F, 0.0F, 1.0F);

        int panelWidth = 216;
        int panelHeight = 96;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2 - 18;

        int panelAlpha = (int) ((1.0F - transfer) * 170.0F);
        int panelBorderAlpha = (int) ((1.0F - transfer) * 210.0F);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, (panelAlpha << 24) | 0x091116);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, (panelBorderAlpha << 24) | 0x93D8E8);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, (panelBorderAlpha << 24) | 0x355B66);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, (panelBorderAlpha << 24) | 0x93D8E8);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, (panelBorderAlpha << 24) | 0x355B66);

        int barX = panelX + 18;
        int barY = panelY + panelHeight - 12;
        int barWidth = panelWidth - 36;
        graphics.fill(barX, barY, barX + barWidth, barY + 4, (75 << 24) | 0x233640);
        graphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 4, ((int) (Mth.clamp(thermalStrength + 0.4F, 0.4F, 1.0F) * 255.0F) << 24) | 0x8FE7F0);

        int hudIconX = width - HUD_WIDTH - 10 + 7;
        int hudIconY = 17;
        int startIconX = panelX + (panelWidth - 24) / 2;
        int startIconY = panelY + 12;
        int iconX = Mth.floor(Mth.lerp(transfer, startIconX, hudIconX));
        int iconY = Mth.floor(Mth.lerp(transfer, startIconY, hudIconY));
        int iconSize = Mth.floor(Mth.lerp(transfer, 24.0F, 16.0F));

        drawScaledLogo(graphics, iconX, iconY, iconSize);

        if (transfer < 1.0F) {
            int textAlpha = rgba(0x9CE3F5, 1.0F - transfer);
            int subTextAlpha = rgba(0x7CA2AE, 1.0F - transfer);
            graphics.drawString(mc.font, "THERMAL LINK // INITIALIZING", centeredX(mc, width, "THERMAL LINK // INITIALIZING"), panelY + 50, textAlpha, false);
            graphics.drawString(mc.font, "Synchronizing visor sensors...", centeredX(mc, width, "Synchronizing visor sensors..."), panelY + 62, subTextAlpha, false);
        }
    }

    private static String fit(Minecraft mc, String text, int maxWidth) {
        return mc.font.plainSubstrByWidth(text, maxWidth);
    }

    private static void drawScaledLogo(GuiGraphics graphics, int x, int y, int size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        float scale = size / 16.0F;
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(ORSA_LOGO, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private static int centeredX(Minecraft mc, int width, String text) {
        return (width - mc.font.width(text)) / 2;
    }

    private static int rgba(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | rgb;
    }
}
