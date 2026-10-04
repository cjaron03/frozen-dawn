package com.frozendawn.client;

import com.frozendawn.init.ModItems;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.data.EmergencyEvaState;
import net.minecraft.network.chat.Component;
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
    private static final int PREFIX_COLOR = OrsaHudPanel.LABEL_COLOR;
    private static final int TANK_PREFIX_COLOR = OrsaHudPanel.MUTED_COLOR;

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

        if (EmergencyEvaHandler.isWearingIssuedPiece(mc.player)) {
            if (mc.player.isCreative() || mc.player.isSpectator()) return;
            int reserve = EmergencyEvaHandler.remainingTicks(mc.player);
            renderReading(graphics, mc, new AirStatusTelemetry.Reading(
                    reserve > 0 ? AirStatusTelemetry.State.EVA_SUPPLY : AirStatusTelemetry.State.VACUUM,
                    new AirStatusTelemetry.TankTelemetry(reserve, EmergencyEvaState.SERVICE_TICKS, 1)), null);
            ContinuityRecoveryHud.render(graphics);
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

        boolean emergency = tankValueOverride == null && EmergencyEvaHandler.isWearingIssuedPiece(mc.player);
        String prefix = emergency ? "" : "AIR:";
        String label = emergency ? Component.translatable(tankTelemetry.hasUsableO2()
                ? "hud.frozendawn.emergency_eva.active" : "hud.frozendawn.emergency_eva.depleted").getString()
                : state.label();
        String tankPrefix = emergency ? Component.translatable("hud.frozendawn.emergency_eva.reserve_label").getString() : "TANK:";
        boolean showModule = !emergency && tankValueOverride == null
                && tankTelemetry.hasAnyTank()
                && O2EfficiencyModuleItem.isInstalled(mc.player);
        String tankValue;
        if (tankValueOverride != null) {
            tankValue = tankValueOverride;
        } else if (tankTelemetry.hasAnyTank()) {
            int eta = emergency ? (tankTelemetry.totalO2() + 19) / 20
                    : smoothEta(mc, AirStatusTelemetry.estimateRemainingSeconds(mc.player, reading));
            tankValue = (emergency ? "" : tankTelemetry.fillPercent() + "%  ")
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

        int textX = OrsaHudPanel.draw(graphics, x, y, totalWidth, PANEL_HEIGHT,
                accentColor, borderColor, badgeColor);
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
            baseColor = OrsaHudPanel.CRITICAL_COLOR;
        } else if (tankTelemetry.fillRatio() <= 0.50F) {
            baseColor = OrsaHudPanel.WARNING_COLOR;
        } else {
            baseColor = OrsaHudPanel.VALUE_COLOR;
        }
        return mixTowardWhite(baseColor, 0.14F * pulse);
    }
}
