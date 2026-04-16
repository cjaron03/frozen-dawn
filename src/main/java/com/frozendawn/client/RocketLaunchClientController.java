package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.network.LaunchSequencePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Locale;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RocketLaunchClientController {
    private static final float VIEWPORT_YAW_LIMIT = 42.0F;
    private static final float VIEWPORT_LOOK_UP_LIMIT = -24.0F;
    private static final float VIEWPORT_LOOK_DOWN_LIMIT = 30.0F;

    private static boolean active;
    private static int entityId = -1;
    private static int countdownTicks;
    private static int liftoffTicks;
    private static int ascentTicks;
    private static int atmosphereExitTicks;
    private static int fadeTicks;
    private static int clientTicks;
    private static CameraType storedCameraType = CameraType.FIRST_PERSON;
    private static Entity storedCameraEntity;
    private static int viewportVehicleId = -1;
    private static float viewportAnchorYaw;

    private RocketLaunchClientController() {
    }

    public static void begin(LaunchSequencePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        active = true;
        entityId = payload.entityId();
        countdownTicks = payload.countdownTicks();
        liftoffTicks = payload.liftoffTicks();
        ascentTicks = payload.ascentTicks();
        atmosphereExitTicks = payload.atmosphereExitTicks();
        fadeTicks = payload.fadeTicks();
        clientTicks = 0;
        storedCameraType = mc.options.getCameraType();
        storedCameraEntity = mc.getCameraEntity();
        if (mc.screen != null) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset(mc);
            return;
        }
        enforceRocketViewportLook(mc);

        if (!active) {
            return;
        }
        if (!mc.isPaused()) {
            clientTicks++;
        }

        Entity rocket = mc.level.getEntity(entityId);
        suppressInput(mc);
        mc.player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mc.player.hurtMarked = true;

        if (rocket == null && clientTicks >= getTotalSequenceTicks() - 2) {
            reset(mc);
            return;
        }

        if (rocket != null) {
            if (clientTicks < countdownTicks) {
                mc.setCameraEntity(rocket);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            } else if (clientTicks < countdownTicks + liftoffTicks) {
                mc.setCameraEntity(rocket);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else {
                if (isCockpitView(mc, rocket)) {
                    mc.setCameraEntity(mc.player);
                } else {
                    mc.setCameraEntity(rocket);
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }

        if (clientTicks >= getTotalSequenceTicks() + 10) {
            reset(mc);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        boolean viewportActive = isRocketViewportActive(mc);
        if (viewportActive) {
            event.setYaw(getClampedViewportYaw(mc));
            event.setPitch(getClampedViewportPitch(mc));
        }

        if (!active) {
            return;
        }
        Entity camera = mc.getCameraEntity();
        Entity rocket = mc.level.getEntity(entityId);
        boolean cockpitView = rocket != null && isCockpitView(mc, rocket);
        if (camera == null || (!cockpitView && camera.getId() != entityId)) {
            return;
        }
        if (clientTicks < countdownTicks + liftoffTicks) {
            return;
        }

        if (!viewportActive) {
            event.setYaw(mc.player.getYRot());
            event.setPitch(mc.player.getXRot());
        }

        float flightProgress = getPostLiftoffProgress();
        float roll = Mth.sin((clientTicks - countdownTicks - liftoffTicks) * 0.06F) * 1.4F;
        event.setRoll(roll * Math.min(1.0F, flightProgress * 1.5F));
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof RocketLaunchEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (shouldShowRocketViewport(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (shouldShowRocketViewport(mc)) {
            renderRocketViewport(event.getGuiGraphics(), mc.font, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        var font = mc.font;

        if (shouldShowRocketViewport(mc) && mc.screen == null) {
            renderRocketViewport(graphics, font, width, height);
        }

        if (!active) {
            return;
        }

        float fade = getFadeAmount();
        float surfaceFade = getSurfaceFadeAmount();
        float ascentBlend = getPostLiftoffProgress();
        if (clientTicks < countdownTicks) {
            int seconds = Mth.ceil((countdownTicks - clientTicks) / 20.0F);
            String countdown = "T-" + Math.max(0, seconds);
            graphics.fill(0, 0, width, 26, 0x88000000);
            graphics.drawCenteredString(font, Component.literal("LAUNCH SEQUENCE"), width / 2, 6, 0xD9F1FF);
            graphics.drawCenteredString(font, Component.literal(countdown), width / 2, 16, 0xFFF5C2);
        }

        if (surfaceFade > 0.0F) {
            int lowerAlpha = Mth.clamp((int) (surfaceFade * 165.0F), 0, 165);
            int upperAlpha = Mth.clamp((int) (surfaceFade * 68.0F), 0, 68);
            graphics.fillGradient(0, height / 3, width, height, (upperAlpha << 24) | 0x03070D, (lowerAlpha << 24) | 0x020407);
            int horizonBandAlpha = Mth.clamp((int) ((1.0F - ascentBlend) * 54.0F), 0, 54);
            if (horizonBandAlpha > 0) {
                graphics.fillGradient(0, height / 2 - 24, width, height / 2 + 16, 0x00000000,
                        (horizonBandAlpha << 24) | 0x38556B);
            }
        }

        if (fade > 0.0F) {
            int alpha = Mth.clamp((int) (fade * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, alpha << 24);
        }
    }

    private static float getFadeAmount() {
        int fadeStart = getTotalSequenceTicks() - fadeTicks;
        if (clientTicks <= fadeStart) {
            return 0.0F;
        }
        return Mth.clamp((clientTicks - fadeStart) / (float) fadeTicks, 0.0F, 1.0F);
    }

    private static float getSurfaceFadeAmount() {
        if (clientTicks <= countdownTicks) {
            return 0.0F;
        }
        int surfaceStart = countdownTicks + Math.max(20, liftoffTicks / 2);
        int surfaceEnd = countdownTicks + liftoffTicks + ascentTicks + atmosphereExitTicks;
        if (clientTicks <= surfaceStart) {
            return 0.0F;
        }
        return Mth.clamp((clientTicks - surfaceStart) / (float) Math.max(1, surfaceEnd - surfaceStart), 0.0F, 1.0F);
    }

    private static float getPostLiftoffProgress() {
        int ascentStart = countdownTicks + liftoffTicks;
        int ascentEnd = ascentStart + ascentTicks + atmosphereExitTicks;
        if (clientTicks <= ascentStart) {
            return 0.0F;
        }
        return Mth.clamp((clientTicks - ascentStart) / (float) Math.max(1, ascentEnd - ascentStart), 0.0F, 1.0F);
    }

    private static int getTotalSequenceTicks() {
        return countdownTicks + liftoffTicks + ascentTicks + atmosphereExitTicks + fadeTicks;
    }

    private static boolean isCockpitView(Minecraft mc, Entity rocket) {
        return rocket != null
                && mc.player != null
                && clientTicks >= countdownTicks + liftoffTicks
                && mc.player.getVehicle() == rocket;
    }

    public static boolean shouldRenderExteriorRocket(Entity rocket) {
        Minecraft mc = Minecraft.getInstance();
        if (rocket == null || mc.player == null) {
            return true;
        }
        if (isRocketViewportActive(mc)) {
            return false;
        }
        boolean localRider = isSameEntity(mc.player.getVehicle(), rocket)
                || isSameEntity(mc.player.getRootVehicle(), rocket)
                || rocket.hasPassenger(mc.player);
        return !localRider || mc.options.getCameraType() != CameraType.FIRST_PERSON;
    }

    private static boolean shouldShowRocketViewport(Minecraft mc) {
        return isRocketViewportActive(mc);
    }

    private static boolean isRocketViewportActive(Minecraft mc) {
        return mc.player != null
                && mc.player.getVehicle() instanceof RocketLaunchEntity
                && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private static boolean isSameEntity(Entity first, Entity second) {
        return first != null && second != null && first.getId() == second.getId();
    }

    private static void enforceRocketViewportLook(Minecraft mc) {
        if (!isRocketViewportActive(mc)) {
            viewportVehicleId = -1;
            return;
        }

        float clampedYaw = getClampedViewportYaw(mc);
        float clampedPitch = getClampedViewportPitch(mc);
        mc.player.setYRot(clampedYaw);
        mc.player.setYHeadRot(clampedYaw);
        mc.player.setYBodyRot(clampedYaw);
        mc.player.setXRot(clampedPitch);
    }

    private static float getClampedViewportYaw(Minecraft mc) {
        ensureViewportAnchor(mc);
        float yawDelta = Mth.wrapDegrees(mc.player.getYRot() - viewportAnchorYaw);
        return viewportAnchorYaw + Mth.clamp(yawDelta, -VIEWPORT_YAW_LIMIT, VIEWPORT_YAW_LIMIT);
    }

    private static float getClampedViewportPitch(Minecraft mc) {
        return Mth.clamp(mc.player.getXRot(), VIEWPORT_LOOK_UP_LIMIT, VIEWPORT_LOOK_DOWN_LIMIT);
    }

    private static void ensureViewportAnchor(Minecraft mc) {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) {
            return;
        }
        if (viewportVehicleId != vehicle.getId()) {
            viewportVehicleId = vehicle.getId();
            viewportAnchorYaw = mc.player.getYRot();
        }
    }

    private static void suppressInput(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    private static void renderRocketViewport(GuiGraphics graphics, net.minecraft.client.gui.Font font, int width, int height) {
        int topRail = Math.max(34, height / 12);
        int bottomRail = Math.max(58, height / 6);
        int sideRail = Math.max(44, width / 10);
        int viewLeft = sideRail;
        int viewRight = width - sideRail;
        int viewTop = topRail;
        int viewBottom = height - bottomRail;

        graphics.fill(0, 0, width, topRail, 0xEA10141A);
        graphics.fill(0, viewBottom, width, height, 0xF010141A);
        graphics.fill(0, topRail, sideRail, viewBottom, 0xDE10141A);
        graphics.fill(width - sideRail, topRail, width, viewBottom, 0xDE10141A);

        graphics.fill(viewLeft - 8, viewTop - 8, viewRight + 8, viewTop + 5, 0xCC303844);
        graphics.fill(viewLeft - 8, viewBottom - 5, viewRight + 8, viewBottom + 10, 0xCC303844);
        graphics.fill(viewLeft - 10, viewTop - 4, viewLeft + 5, viewBottom + 6, 0xCC252C36);
        graphics.fill(viewRight - 5, viewTop - 4, viewRight + 10, viewBottom + 6, 0xCC252C36);

        graphics.fill(viewLeft, viewTop, viewRight, viewTop + 2, 0xCC4FC7DA);
        graphics.fill(viewLeft, viewBottom - 2, viewRight, viewBottom, 0x994FC7DA);
        graphics.fill(viewLeft, viewTop, viewLeft + 2, viewBottom, 0x884FC7DA);
        graphics.fill(viewRight - 2, viewTop, viewRight, viewBottom, 0x884FC7DA);

        int corner = Math.max(18, Math.min(width, height) / 18);
        drawCornerBracket(graphics, viewLeft + 4, viewTop + 4, corner, true, true);
        drawCornerBracket(graphics, viewRight - 4, viewTop + 4, corner, false, true);
        drawCornerBracket(graphics, viewLeft + 4, viewBottom - 4, corner, true, false);
        drawCornerBracket(graphics, viewRight - 4, viewBottom - 4, corner, false, false);

        graphics.fillGradient(viewLeft + 4, viewTop + 3, viewRight - 4, viewTop + Math.max(34, height / 11),
                0x244FC7DA, 0x00000000);
        graphics.fillGradient(viewLeft + 4, viewBottom - Math.max(24, height / 14), viewRight - 4, viewBottom - 3,
                0x00000000, 0x18283440);
        graphics.fillGradient(viewLeft + 8, viewTop + 8, viewRight - 8, viewBottom - 8,
                0x101E5968, 0x08070B10);

        int glareY = viewTop + Math.max(14, (viewBottom - viewTop) / 5);
        graphics.fill(viewLeft + 24, glareY, viewRight - 38, glareY + 1, 0x558EEAFF);
        graphics.fill(viewLeft + 44, glareY + 16, viewRight - 92, glareY + 17, 0x2255C6D8);

        int statusY = Math.max(6, viewTop - 25);
        graphics.fill(viewLeft + 10, statusY - 3, viewLeft + 142, statusY + 11, 0x6610141A);
        graphics.drawString(font, Component.literal("ORSA VIEWPORT"), viewLeft + 15, statusY, 0x8DEAFF, false);
        renderRocketTelemetryPanel(graphics, font, viewRight, viewBottom);
    }

    private static void renderRocketTelemetryPanel(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                                   int viewRight, int viewBottom) {
        double altitudeKm = getRocketAltitudeKm();
        String altitude = formatAltitude(altitudeKm);
        String phase = getAltitudePhaseLabel(altitudeKm);
        Minecraft mc = Minecraft.getInstance();
        float fill = getRocketFuelFill(mc);
        String fuel = getRocketFuelValue(mc, fill);

        int panelWidth = 210;
        int panelHeight = 44;
        int panelX = viewRight - panelWidth - 10;
        int panelY = viewBottom + 12;
        int labelX = panelX + 8;
        int valueX = panelX + 44;
        int barX = panelX + 104;
        int barWidth = 72;
        int barHeight = 5;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x7710141A);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + panelHeight - 2, 0x55242C35);

        int altY = panelY + 7;
        graphics.drawString(font, Component.literal("ALT"), labelX, altY, 0x8DEAFF, false);
        graphics.drawString(font, Component.literal(altitude), valueX, altY, getAltitudeTextColor(altitudeKm), false);
        graphics.drawString(font, Component.literal(phase), panelX + panelWidth - 8 - font.width(phase), altY, 0xB8EFFF, false);
        drawTelemetryBar(graphics, barX, altY + 9, barWidth, barHeight,
                getAltitudeProgress(altitudeKm), getAltitudeBarColor(altitudeKm));
        int karmanX = barX + Mth.clamp((int) (100.0D / 160.0D * barWidth), 0, barWidth);
        graphics.fill(karmanX, altY + 7, karmanX + 1, altY + 16, 0xAA8DEAFF);

        int fuelY = panelY + 25;
        graphics.drawString(font, Component.literal("FUEL"), labelX, fuelY, 0x8DEAFF, false);
        graphics.drawString(font, Component.literal(fuel), valueX, fuelY, getRocketFuelTextColor(fill), false);
        drawTelemetryBar(graphics, barX, fuelY + 9, barWidth, barHeight, fill, getRocketFuelBarColor(fill));
        for (int i = 1; i < 6; i++) {
            int tickX = barX + i * barWidth / 6;
            graphics.fill(tickX, fuelY + 8, tickX + 1, fuelY + 15, 0x664FC7DA);
        }
    }

    private static void drawTelemetryBar(GuiGraphics graphics, int x, int y, int width, int height,
                                         float fill, int fillColor) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xCC05080C);
        graphics.fill(x, y, x + width, y + height, 0xAA17202A);
        int filledWidth = Mth.clamp((int) (fill * width), 0, width);
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, fillColor);
        }
    }

    private static float getRocketFuelFill(Minecraft mc) {
        if (active && isRocketViewportActive(mc)) {
            if (clientTicks < countdownTicks) {
                return 1.0F;
            }
            int burnTicks = Math.max(1, liftoffTicks + ascentTicks + atmosphereExitTicks);
            float burnProgress = (clientTicks - countdownTicks) / (float) burnTicks;
            return 1.0F - Mth.clamp(burnProgress, 0.0F, 1.0F);
        }
        if (mc.player != null && mc.player.getVehicle() instanceof RocketLaunchEntity rocket) {
            return rocket.getFuelCells() / 6.0F;
        }
        return 0.0F;
    }

    private static String getRocketFuelValue(Minecraft mc, float fill) {
        if (active && isRocketViewportActive(mc)) {
            return clientTicks < countdownTicks ? "ARMED" : Mth.ceil(fill * 100.0F) + "%";
        }
        if (mc.player != null && mc.player.getVehicle() instanceof RocketLaunchEntity rocket) {
            return rocket.getFuelCells() + "/6";
        }
        return "0/6";
    }

    private static int getRocketFuelTextColor(float fill) {
        if (fill >= 1.0F) {
            return 0xA7FFEA;
        }
        if (fill >= 0.34F) {
            return 0xFFF5A6;
        }
        return 0xFF8B8B;
    }

    private static int getRocketFuelBarColor(float fill) {
        if (fill >= 1.0F) {
            return 0xD44FE6D8;
        }
        if (fill >= 0.34F) {
            return 0xD8FFB44F;
        }
        return 0xD8FF5C5C;
    }

    private static double getRocketAltitudeKm() {
        if (!active || clientTicks <= countdownTicks) {
            return 0.0D;
        }

        int postCountdown = clientTicks - countdownTicks;
        if (postCountdown <= liftoffTicks) {
            double progress = postCountdown / (double) Math.max(1, liftoffTicks);
            return 8.0D * progress * progress;
        }

        postCountdown -= liftoffTicks;
        if (postCountdown <= ascentTicks) {
            double progress = postCountdown / (double) Math.max(1, ascentTicks);
            return 8.0D + 152.0D * Math.pow(progress, 1.45D);
        }

        postCountdown -= ascentTicks;
        double progress = postCountdown / (double) Math.max(1, atmosphereExitTicks);
        return 160.0D + 25.0D * Math.sqrt(Mth.clamp((float) progress, 0.0F, 1.0F));
    }

    private static float getAltitudeProgress(double altitudeKm) {
        return Mth.clamp((float) (altitudeKm / 160.0D), 0.0F, 1.0F);
    }

    private static String formatAltitude(double altitudeKm) {
        if (altitudeKm < 100.0D) {
            return String.format(Locale.ROOT, "%.1f KM", altitudeKm);
        }
        return String.format(Locale.ROOT, "%.0f KM", altitudeKm);
    }

    private static String getAltitudePhaseLabel(double altitudeKm) {
        if (!active || clientTicks <= countdownTicks || altitudeKm < 1.0D) {
            return "GROUND";
        }
        if (altitudeKm < 35.0D) {
            return "MAX-Q";
        }
        if (altitudeKm < 100.0D) {
            return "ASCENT";
        }
        if (altitudeKm < 160.0D) {
            return "KARMAN";
        }
        return "LEO XFER";
    }

    private static int getAltitudeTextColor(double altitudeKm) {
        if (altitudeKm >= 160.0D) {
            return 0xA7FFEA;
        }
        if (altitudeKm >= 100.0D) {
            return 0xFFF5A6;
        }
        return 0xD9F1FF;
    }

    private static int getAltitudeBarColor(double altitudeKm) {
        if (altitudeKm >= 160.0D) {
            return 0xD44FE6D8;
        }
        if (altitudeKm >= 100.0D) {
            return 0xD8FFB44F;
        }
        return 0xD04FC7DA;
    }

    private static void drawCornerBracket(GuiGraphics graphics, int x, int y, int size, boolean left, boolean top) {
        int color = 0xCC7DDFF1;
        int thickness = 2;
        int horizontalStart = left ? x : x - size;
        int horizontalEnd = left ? x + size : x;
        int verticalStart = top ? y : y - size;
        int verticalEnd = top ? y + size : y;
        graphics.fill(horizontalStart, y - (top ? 0 : thickness), horizontalEnd, y + (top ? thickness : 0), color);
        graphics.fill(x - (left ? 0 : thickness), verticalStart, x + (left ? thickness : 0), verticalEnd, color);
    }

    private static void reset(Minecraft mc) {
        if (mc.player != null) {
            mc.setCameraEntity(storedCameraEntity != null ? storedCameraEntity : mc.player);
        }
        mc.options.setCameraType(storedCameraType);
        active = false;
        entityId = -1;
        countdownTicks = 0;
        liftoffTicks = 0;
        ascentTicks = 0;
        atmosphereExitTicks = 0;
        fadeTicks = 0;
        clientTicks = 0;
        storedCameraEntity = null;
    }
}
