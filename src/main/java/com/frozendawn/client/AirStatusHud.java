package com.frozendawn.client;

import com.frozendawn.init.ModItems;
import com.frozendawn.item.O2EfficiencyModuleItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Compact EVA air-state readout stacked under the temperature HUD.
 */
public final class AirStatusHud {

    private static final int PANEL_GAP = 2;
    private static final int BADGE_SIZE = 8;
    private static final int ACCENT_WIDTH = 2;
    private static final int BADGE_GAP = 3;
    private static final int LABEL_GAP = 4;
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 2;
    private static final int PANEL_HEIGHT = 22;
    private static final int PULSE_DURATION = 12;
    private static final int MODULE_ICON_SIZE = 8;
    private static final int MODULE_ICON_GAP = 3;
    private static final float ETA_SMOOTHING = 0.22F;
    private static final int BG_COLOR = 0xAA0B1217;
    private static final int PREFIX_COLOR = 0xFF8A9AA4;
    private static final int TANK_PREFIX_COLOR = 0xFF6F7F89;

    private static AirStatusTelemetry.State lastState = null;
    private static int pulseTicks = 0;
    private static int lastEtaTick = Integer.MIN_VALUE;
    private static float displayedEtaSeconds = AirStatusEtaPolicy.NO_ACTIVE_DRAIN;

    private AirStatusHud() {
    }

    public static void reset() {
        lastState = null;
        pulseTicks = 0;
        lastEtaTick = Integer.MIN_VALUE;
        displayedEtaSeconds = AirStatusEtaPolicy.NO_ACTIVE_DRAIN;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (OrsaAwakeningIntro.shouldSuppressSurvivalHud()) {
            return;
        }
        if (mc.player == null || mc.options.hideGui) {
            reset();
            return;
        }

        if (MasterArchitectFloodClient.shouldCorruptSuitTelemetry()) {
            renderMindOverride(
                    graphics, MasterArchitectFloodClient.corruptedOxygenText());
            return;
        }
        if (MasterArchitectAuraClient.shouldFlickerO2()) {
            renderMindOverride(graphics, "RECAL...");
            return;
        }

        AirStatusTelemetry.Reading reading = AirStatusTelemetry.resolveReading(mc.player);
        if (reading == null) {
            reset();
            return;
        }
        renderReading(graphics, mc, reading, null);
    }

    /** Draws the existing EVA panel with a temporary mind-stage tank value. */
    static void renderMindOverride(GuiGraphics graphics, String tankValueOverride) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui
                || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }
        AirStatusTelemetry.TankTelemetry tankTelemetry =
                AirStatusTelemetry.getTankTelemetry(mc.player);
        renderReading(
                graphics,
                mc,
                new AirStatusTelemetry.Reading(
                        AirStatusTelemetry.State.EVA_SUPPLY, tankTelemetry),
                tankValueOverride);
    }

    private static void renderReading(
            GuiGraphics graphics,
            Minecraft mc,
            AirStatusTelemetry.Reading reading,
            String tankValueOverride) {
        AirStatusTelemetry.State state = reading.state();
        AirStatusTelemetry.TankTelemetry tankTelemetry = reading.tankTelemetry();

        if (state != lastState) {
            lastState = state;
            pulseTicks = PULSE_DURATION;
        } else if (pulseTicks > 0) {
            pulseTicks--;
        }

        String prefix = "AIR:";
        String label = state.label();
        String tankPrefix = "TANK:";
        boolean showModule = tankValueOverride == null
                && tankTelemetry.hasAnyTank()
                && O2EfficiencyModuleItem.isInstalled(mc.player);
        String tankValue;
        if (tankValueOverride != null) {
            tankValue = tankValueOverride;
        } else if (tankTelemetry.hasAnyTank()) {
            int eta = smoothEta(mc, AirStatusTelemetry.estimateRemainingSeconds(
                    mc.player, reading));
            tankValue = tankTelemetry.fillPercent() + "%  "
                    + AirStatusEtaPolicy.format(eta);
        } else {
            tankValue = "NONE";
        }
        int prefixWidth = mc.font.width(prefix);
        int labelWidth = mc.font.width(label);
        int tankPrefixWidth = mc.font.width(tankPrefix);
        int tankValueWidth = mc.font.width(tankValue);
        int contentWidth = Math.max(
                prefixWidth + 3 + labelWidth,
                tankPrefixWidth + 3 + tankValueWidth
                        + (showModule ? MODULE_ICON_GAP + MODULE_ICON_SIZE : 0)
        );
        int totalWidth = PADDING_X * 2
                + ACCENT_WIDTH
                + BADGE_GAP
                + BADGE_SIZE
                + LABEL_GAP
                + contentWidth;

        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + TemperatureHud.TOTAL_HEIGHT + PANEL_GAP;

        float pulse = pulseTicks > 0 ? pulseTicks / (float) PULSE_DURATION : 0.0F;
        int accentColor = mixTowardWhite(state.accentColor(), 0.28F * pulse);
        int textColor = mixTowardWhite(state.textColor(), 0.18F * pulse);
        int borderColor = mixTowardWhite(state.accentColor(), 0.42F * pulse);
        int badgeColor = mixTowardWhite(state.badgeColor(), 0.12F * pulse);
        int tankValueColor = getTankValueColor(tankTelemetry, pulse);

        graphics.fill(x + 1, y, x + totalWidth - 1, y + PANEL_HEIGHT, BG_COLOR);
        graphics.fill(x, y + 1, x + totalWidth, y + PANEL_HEIGHT - 1, BG_COLOR);

        graphics.fill(x, y + 1, x + ACCENT_WIDTH, y + PANEL_HEIGHT - 1, accentColor);
        graphics.fill(x + 1, y, x + totalWidth - 1, y + 1, withAlpha(borderColor, 210));
        graphics.fill(x + 1, y + PANEL_HEIGHT - 1, x + totalWidth - 1, y + PANEL_HEIGHT, withAlpha(borderColor, 160));
        graphics.fill(x + totalWidth - 1, y + 1, x + totalWidth, y + PANEL_HEIGHT - 1, withAlpha(borderColor, 185));

        int badgeX = x + PADDING_X + ACCENT_WIDTH + BADGE_GAP - 1;
        int badgeY = y + (PANEL_HEIGHT - BADGE_SIZE) / 2;
        float r = FastColor.ARGB32.red(badgeColor) / 255.0F;
        float g = FastColor.ARGB32.green(badgeColor) / 255.0F;
        float b = FastColor.ARGB32.blue(badgeColor) / 255.0F;
        float a = FastColor.ARGB32.alpha(badgeColor) / 255.0F;
        OrsaLogoRenderer.drawTinted(graphics, badgeX, badgeY, BADGE_SIZE, r, g, b, a);

        int textX = badgeX + BADGE_SIZE + LABEL_GAP;
        int airTextY = y + PADDING_Y + 1;
        int tankTextY = airTextY + 9;
        graphics.drawString(mc.font, prefix, textX, airTextY, mixTowardWhite(PREFIX_COLOR, 0.10F * pulse), false);
        graphics.drawString(mc.font, label, textX + prefixWidth + 3, airTextY, textColor, false);
        graphics.drawString(mc.font, tankPrefix, textX, tankTextY, mixTowardWhite(TANK_PREFIX_COLOR, 0.08F * pulse), false);
        int tankValueX = textX + tankPrefixWidth + 3;
        graphics.drawString(mc.font, tankValue, tankValueX, tankTextY, tankValueColor, false);
        if (showModule) {
            renderModuleIcon(
                    graphics,
                    tankValueX + tankValueWidth + MODULE_ICON_GAP,
                    tankTextY);
        }
    }

    private static int smoothEta(Minecraft minecraft, int targetSeconds) {
        if (targetSeconds < 0) {
            displayedEtaSeconds = AirStatusEtaPolicy.NO_ACTIVE_DRAIN;
            lastEtaTick = minecraft.player.tickCount;
            return AirStatusEtaPolicy.NO_ACTIVE_DRAIN;
        }
        int currentTick = minecraft.player.tickCount;
        if (displayedEtaSeconds < 0.0F || lastEtaTick == Integer.MIN_VALUE) {
            displayedEtaSeconds = targetSeconds;
        } else if (currentTick != lastEtaTick) {
            int elapsed = Math.max(1, currentTick - lastEtaTick);
            float weight = 1.0F - (float) Math.pow(1.0F - ETA_SMOOTHING, elapsed);
            displayedEtaSeconds = Mth.lerp(weight, displayedEtaSeconds, targetSeconds);
        }
        lastEtaTick = currentTick;
        return Math.max(0, Math.round(displayedEtaSeconds));
    }

    private static void renderModuleIcon(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(new ItemStack(ModItems.O2_EFFICIENCY_MODULE.get()), 0, 0);
        graphics.pose().popPose();
    }

    private static int mixTowardWhite(int color, float amount) {
        int a = FastColor.ARGB32.alpha(color);
        int r = FastColor.ARGB32.red(color);
        int g = FastColor.ARGB32.green(color);
        int b = FastColor.ARGB32.blue(color);
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        r = Mth.floor(Mth.lerp(clamped, r, 255));
        g = Mth.floor(Mth.lerp(clamped, g, 255));
        b = Mth.floor(Mth.lerp(clamped, b, 255));
        return FastColor.ARGB32.color(a, r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return FastColor.ARGB32.color(
                alpha,
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color)
        );
    }

    private static int getTankValueColor(AirStatusTelemetry.TankTelemetry tankTelemetry, float pulse) {
        int baseColor;
        if (!tankTelemetry.hasAnyTank()) {
            baseColor = 0xFF8B939A;
        } else if (tankTelemetry.fillRatio() <= 0.20F) {
            baseColor = 0xFFFFB1B1;
        } else if (tankTelemetry.fillRatio() <= 0.50F) {
            baseColor = 0xFFFFE0A8;
        } else {
            baseColor = 0xFFCDEFFF;
        }
        return mixTowardWhite(baseColor, 0.14F * pulse);
    }
}
