package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.vision.VisionMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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

        VisionMode activeMode = SurveyorLensVision.getActiveVisionMode();
        if (activeMode == VisionMode.THERMAL && SurveyorLensVision.isThermalModeVisible()) {
            float thermalStrength = SurveyorLensVision.getThermalModeStrength();
            SurveyorLensVision.syncThermalShaderUniforms(deltaTracker.getGameTimeDeltaPartialTick(false));
            drawThermalWash(graphics, mc, width, height, thermalStrength);
            if (SurveyorLensVision.isThermalBooting()) {
                drawBootSequence(graphics, mc, width, height, thermalStrength);
            } else {
                float hudStrength = thermalStrength;
                if (SurveyorLensVision.isThermalShuttingDown()) {
                    float shutdownProgress = SurveyorLensVision.getThermalShutdownProgress();
                    hudStrength *= (1.0F - shutdownProgress);
                    drawShutdownFade(graphics, mc, width, height, shutdownProgress);
                }

                if (hudStrength > 0.01F) {
                    drawThermalHud(graphics, mc, width, hudStrength);
                }
            }
        } else if (SurveyorLensVision.isBlizzardModeVisible()) {
            float blizzardStrength = SurveyorLensVision.getBlizzardModeStrength();
            drawBlizzardWash(graphics, mc, width, height, blizzardStrength);
            drawBlizzardHud(graphics, mc, width, blizzardStrength);
        }
    }

    private static void drawThermalWash(GuiGraphics graphics, Minecraft mc, int width, int height, float thermalStrength) {
        int vignetteAlpha = (int) (Mth.clamp(thermalStrength, 0.15F, 1.0F) * 54.0F);
        graphics.fill(0, 0, width, height, (vignetteAlpha << 24) | 0x04070B);

        int edgeAlpha = (int) (thermalStrength * 88.0F);
        int edgeColor = (edgeAlpha << 24) | 0x0A1118;
        int transparent = 0x000A1118;
        int borderSize = (int) (height * 0.15F);
        int sideBorder = (int) (width * 0.09F);
        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        int scanAlphaBase = (int) (thermalStrength * 24.0F);
        for (int y = 0; y < height; y += 3) {
            int lineAlpha = scanAlphaBase + (((y + (int) (time * 2L)) / 3) % 3 == 0 ? 10 : 0);
            graphics.fill(0, y, width, y + 1, (lineAlpha << 24) | 0x6E95A9);
        }

        int bandCount = 5;
        for (int i = 0; i < bandCount; i++) {
            float bandPhase = ((time * 0.04F) + (i * 0.19F)) % 1.0F;
            int bandY = (int) (bandPhase * height);
            int bandHeight = 6 + (i % 3);
            int bandAlpha = (int) (thermalStrength * (9 + i * 3));
            graphics.fillGradient(0, bandY, width, bandY + bandHeight, 0x00000000, (bandAlpha << 24) | 0x7097A8);
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

    private static void drawShutdownFade(GuiGraphics graphics, Minecraft mc, int width, int height, float progress) {
        float eased = 1.0F - progress;
        eased *= eased;
        int fadeAlpha = (int) (eased * 120.0F);
        if (fadeAlpha > 0) {
            graphics.fill(0, 0, width, height, (fadeAlpha << 24) | 0x020507);
        }

        int vignetteAlpha = (int) (eased * 56.0F);
        if (vignetteAlpha > 0) {
            int vignetteColor = (vignetteAlpha << 24) | 0x050A0D;
            int transparent = 0x00050A0D;
            int borderSize = (int) (height * 0.18F);
            int sideBorder = (int) (width * 0.10F);
            graphics.fillGradient(0, 0, width, borderSize, vignetteColor, transparent);
            graphics.fillGradient(0, height - borderSize, width, height, transparent, vignetteColor);
            graphics.fillGradient(0, 0, sideBorder, height, vignetteColor, transparent);
            graphics.fillGradient(width - sideBorder, 0, width, height, transparent, vignetteColor);
        }

        float textVisibility = Mth.clamp(1.0F - progress * 0.8F, 0.0F, 1.0F);
        if (textVisibility > 0.02F) {
            String text = "SHUTTING DOWN...";
            int textColor = rgba(0xA7E8F4, textVisibility);
            int shadowColor = rgba(0x10242C, textVisibility * 0.72F);
            int x = centeredX(mc, width, text);
            int y = height / 2 - 6;
            graphics.drawString(mc.font, text, x + 1, y + 1, shadowColor, false);
            graphics.drawString(mc.font, text, x, y, textColor, false);
        }
    }

    private static void drawBlizzardWash(GuiGraphics graphics, Minecraft mc, int width, int height, float blizzardStrength) {
        int washAlpha = (int) (Mth.clamp(blizzardStrength, 0.12F, 1.0F) * 28.0F);
        graphics.fill(0, 0, width, height, (washAlpha << 24) | 0x1C3656);

        int edgeAlpha = (int) (blizzardStrength * 96.0F);
        int edgeColor = (edgeAlpha << 24) | 0x7CB5F4;
        int transparent = 0x007CB5F4;
        int borderSize = (int) (height * 0.15F);
        int sideBorder = (int) (width * 0.08F);
        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        int sweepY = (int) ((time * 1.7F) % Math.max(1, height + 24)) - 12;
        int sweepAlpha = (int) (blizzardStrength * 48.0F);
        graphics.fillGradient(0, sweepY, width, sweepY + 12, 0x00000000, (sweepAlpha << 24) | 0xA9D4FF);

        int frameAlpha = (int) (blizzardStrength * 188.0F);
        int frameColor = (frameAlpha << 24) | 0xB7E0FF;
        int frameTransparent = 0x00B7E0FF;
        int inset = 10;
        graphics.fillGradient(inset, inset, inset + 20, inset + 2, frameColor, frameTransparent);
        graphics.fillGradient(inset, inset, inset + 2, inset + 20, frameColor, frameTransparent);
        graphics.fillGradient(width - inset - 20, inset, width - inset, inset + 2, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 2, inset, width - inset, inset + 20, frameColor, frameTransparent);
        graphics.fillGradient(inset, height - inset - 2, inset + 20, height - inset, frameColor, frameTransparent);
        graphics.fillGradient(inset, height - inset - 20, inset + 2, height - inset, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 20, height - inset - 2, width - inset, height - inset, frameTransparent, frameColor);
        graphics.fillGradient(width - inset - 2, height - inset - 20, width - inset, height - inset, frameTransparent, frameColor);
    }

    private static void drawBlizzardHud(GuiGraphics graphics, Minecraft mc, int width, float blizzardStrength) {
        int panelWidth = 152;
        int panelHeight = 44;
        int panelX = width - panelWidth - 10;
        int panelY = 10;
        int bgAlpha = (int) (Mth.clamp(blizzardStrength + 0.2F, 0.25F, 1.0F) * 146.0F);
        int borderAlpha = (int) (Mth.clamp(blizzardStrength + 0.15F, 0.2F, 1.0F) * 210.0F);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, (bgAlpha << 24) | 0x09131B);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, (borderAlpha << 24) | 0xB3DFFF);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, (borderAlpha << 24) | 0x4A7CAA);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, (borderAlpha << 24) | 0xB3DFFF);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, (borderAlpha << 24) | 0x4A7CAA);

        graphics.blit(ORSA_LOGO, panelX + 7, panelY + 7, 0, 0, 16, 16, 16, 16);
        graphics.drawString(mc.font, "BLIZZARD OPTICS", panelX + 30, panelY + 7, 0xE3F4FFFF, false);
        graphics.drawString(mc.font, "FILTERED // 32M VIS", panelX + 30, panelY + 17, 0x95C7FF, false);

        int phase = ApocalypseClientData.getPhase();
        String condition = phase >= 6 ? "PHASE 6 WHITEOUT" : "PHASE 5 WHITEOUT";
        graphics.drawString(mc.font, condition, panelX + 7, panelY + 30, 0xCBE7FF, false);
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

    private static void drawGlow(GuiGraphics graphics, int centerX, int centerY, int radius, int color, int coreColor, float intensity, boolean primary) {
        int outerAlpha = (int) (intensity * (primary ? 88.0F : 62.0F));
        int midAlpha = (int) (intensity * (primary ? 132.0F : 98.0F));
        int coreAlpha = (int) (intensity * (primary ? 220.0F : 180.0F));

        int outerRadius = radius + (primary ? 6 : 3);
        int midRadius = (int) (radius * 0.62F);
        int coreRadius = Math.max(2, (int) (radius * 0.22F));

        graphics.fill(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius, centerY + outerRadius, rgba(color, outerAlpha / 255.0F));
        graphics.fill(centerX - midRadius, centerY - midRadius, centerX + midRadius, centerY + midRadius, rgba(color, midAlpha / 255.0F));
        graphics.fill(centerX - coreRadius, centerY - coreRadius, centerX + coreRadius, centerY + coreRadius, rgba(coreColor, coreAlpha / 255.0F));

        if (primary) {
            int ringRadius = outerRadius + 5;
            int ringAlpha = (int) (intensity * 92.0F);
            int ringColor = rgba(coreColor, ringAlpha / 255.0F);
            graphics.fill(centerX - ringRadius, centerY - 1, centerX + ringRadius, centerY + 1, ringColor);
            graphics.fill(centerX - 1, centerY - ringRadius, centerX + 1, centerY + ringRadius, ringColor);
        }
    }

    private static void drawEdgeCue(GuiGraphics graphics, int width, int height, float xNdc, float yNdc, int color, float intensity, boolean primary) {
        int inset = 16;
        int x = Mth.clamp((int) ((xNdc * 0.5F + 0.5F) * width), inset, width - inset);
        int y = Mth.clamp((int) ((0.5F - yNdc * 0.5F) * height), inset, height - inset);
        int halfLength = primary ? 16 : 10;
        int thickness = primary ? 3 : 2;
        int alpha = (int) (intensity * (primary ? 210.0F : 150.0F));
        int cueColor = rgba(color, alpha / 255.0F);
        graphics.fill(x - halfLength, y - thickness, x + halfLength, y + thickness, cueColor);
        graphics.fill(x - thickness, y - halfLength, x + thickness, y + halfLength, cueColor);
    }

    private static int centeredX(Minecraft mc, int width, String text) {
        return (width - mc.font.width(text)) / 2;
    }

    private static int rgba(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | rgb;
    }
}
