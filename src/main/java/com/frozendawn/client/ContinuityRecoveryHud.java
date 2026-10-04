package com.frozendawn.client;

import com.frozendawn.data.ContinuityRecoveryState;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModAttachments;
import com.mojang.blaze3d.platform.InputConstants;
import com.frozendawn.network.ContinuityTargetPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ContinuityRecoveryHud {
    private static final int PANEL_HEIGHT = 41;
    private static final KeyMapping TARGET_KEY = new KeyMapping("key.frozendawn.continuity_target",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.frozendawn");
    private ContinuityRecoveryHud() {}

    public static KeyMapping targetKey() { return TARGET_KEY; }
    public static void tick() {
        var mc = Minecraft.getInstance();
        while (TARGET_KEY.consumeClick()) {
            if (mc.screen == null && visible()) {
                var record = mc.player.getData(ModAttachments.CONTINUITY_RECOVERY);
                record.selectShelter(!record.shelterSelected());
                PacketDistributor.sendToServer(new ContinuityTargetPayload(
                        mc.player.getData(ModAttachments.EMERGENCY_EVA).issue(), record.shelterSelected()));
            }
        }
    }

    private static boolean visible() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isAlive() && !mc.player.isCreative() && !mc.player.isSpectator()
                && !mc.options.hideGui && !OrsaAwakeningIntro.shouldSuppressSurvivalHud()
                && EmergencyEvaHandler.isWearingIssuedPiece(mc.player)
                && EmergencyEvaHandler.remainingTicks(mc.player) > 0;
    }

    public static int dialogueOffset() { return visible() ? panelHeight() + 2 : 0; }

    private static GlobalPos target() {
        var record = Minecraft.getInstance().player.getData(ModAttachments.CONTINUITY_RECOVERY);
        return record.shelterSelected() ? record.shelterEstimate() : record.telemetry();
    }

    private static int panelHeight() {
        var target = target();
        return PANEL_HEIGHT + (target != null && !target.dimension().equals(
                Minecraft.getInstance().player.level().dimension()) ? 9 : 0);
    }

    public static void render(GuiGraphics graphics) {
        if (!visible()) return;
        var mc = Minecraft.getInstance();
        boolean shelterSelected = mc.player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterSelected();
        GlobalPos target = target();
        Component header = text("protocol");
        Component status;
        Component reading;
        Component coordinates = null;
        int statusColor = OrsaHudPanel.LABEL_COLOR;
        if (target == null) {
            status = text(shelterSelected ? "no_shelter" : "no_telemetry");
            reading = text("record_unavailable");
            statusColor = OrsaHudPanel.WARNING_COLOR;
        } else if (!target.dimension().equals(mc.player.level().dimension())) {
            status = text(shelterSelected ? "shelter_degraded" : "telemetry");
            // Coordinates are useful across dimensions; a compass bearing would be misleading.
            reading = text("dimension", target.dimension().location().toString());
            coordinates = text("coordinates", target.pos().getX(), target.pos().getY(), target.pos().getZ());
            statusColor = OrsaHudPanel.WARNING_COLOR;
        } else {
            double dx = target.pos().getX() + 0.5 - mc.player.getX();
            double dz = target.pos().getZ() + 0.5 - mc.player.getZ();
            double distance = Math.hypot(dx, dz);
            String bearing = ContinuityNavigationPolicy.directionArrow(dx, dz, mc.player.getYRot())
                    + " " + ContinuityNavigationPolicy.bearing(dx, dz);
            if (shelterSelected) {
                status = text(distance <= ContinuityRecoveryState.SEARCH_RADIUS ? "search_area" : "shelter_degraded");
                reading = distance <= ContinuityRecoveryState.SEARCH_RADIUS ? text("search_radius", 32)
                        : text("approximate", bearing,
                                ContinuityNavigationPolicy.approximateDistance(distance));
                statusColor = OrsaHudPanel.WARNING_COLOR;
            } else {
                status = text("telemetry");
                int dy = target.pos().getY() - mc.player.blockPosition().getY();
                Component vertical = text(dy > 3 ? "above" : dy < -3 ? "below" : "level", Math.abs(dy));
                reading = text("equipment", bearing,
                        (int) Math.round(distance), vertical);
            }
        }
        Component hint = text("switch", TARGET_KEY.getTranslatedKeyMessage());
        int x = TemperatureHud.HUD_X;
        int y = TemperatureHud.HUD_Y + TemperatureHud.TOTAL_HEIGHT + 26;
        int maxWidth = Math.max(60, graphics.guiWidth() - x * 2);
        int textWidth = Math.max(Math.max(mc.font.width(header), mc.font.width(status)),
                Math.max(mc.font.width(reading), mc.font.width(hint)));
        if (coordinates != null) textWidth = Math.max(textWidth, mc.font.width(coordinates));
        int width = Math.min(maxWidth, OrsaHudPanel.TEXT_INSET + textWidth + 4);
        var palette = AirStatusTelemetry.State.EVA_SUPPLY;
        int textX = OrsaHudPanel.draw(graphics, x, y, width, panelHeight(),
                palette.accentColor(), palette.accentColor(), palette.badgeColor());
        graphics.drawString(mc.font, fit(header, width), textX, y + 3, palette.textColor(), false);
        graphics.drawString(mc.font, fit(status, width), textX, y + 12, statusColor, false);
        graphics.drawString(mc.font, fit(reading, width), textX, y + 21, OrsaHudPanel.VALUE_COLOR, false);
        if (coordinates != null) graphics.drawString(mc.font, fit(coordinates, width), textX, y + 30,
                OrsaHudPanel.VALUE_COLOR, false);
        graphics.drawString(mc.font, fit(hint, width), textX, y + 30 + (coordinates == null ? 0 : 9),
                OrsaHudPanel.MUTED_COLOR, false);
    }

    private static String fit(Component line, int width) {
        return Minecraft.getInstance().font.plainSubstrByWidth(line.getString(),
                Math.max(1, width - OrsaHudPanel.TEXT_INSET - 4));
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("hud.frozendawn.continuity." + key, args);
    }
}
