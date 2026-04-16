package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.LaunchSequencePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RocketLaunchClientController {
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
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset(mc);
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
            mc.setCameraEntity(rocket);
            if (clientTicks < countdownTicks) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            } else if (clientTicks < countdownTicks + liftoffTicks) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }

        if (clientTicks >= getTotalSequenceTicks() + 10) {
            reset(mc);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Entity camera = mc.getCameraEntity();
        if (camera == null || camera.getId() != entityId) {
            return;
        }
        if (clientTicks < countdownTicks + liftoffTicks) {
            return;
        }

        event.setYaw(mc.player.getYRot());
        event.setPitch(mc.player.getXRot());

        float flightProgress = getPostLiftoffProgress();
        float roll = Mth.sin((clientTicks - countdownTicks - liftoffTicks) * 0.06F) * 1.4F;
        event.setRoll(roll * Math.min(1.0F, flightProgress * 1.5F));
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = mc.font;

        float fade = getFadeAmount();
        float surfaceFade = getSurfaceFadeAmount();
        float ascentBlend = getPostLiftoffProgress();
        if (clientTicks < countdownTicks) {
            int seconds = Mth.ceil((countdownTicks - clientTicks) / 20.0F);
            String countdown = "T-" + Math.max(0, seconds);
            graphics.fill(0, 0, width, 26, 0x88000000);
            graphics.drawCenteredString(font, Component.literal("LAUNCH SEQUENCE"), width / 2, 6, 0xD9F1FF);
            graphics.drawCenteredString(font, Component.literal(countdown), width / 2, 16, 0xFFF5C2);
        } else {
            String stage = getStageLabel();
            graphics.fill(0, 0, width, 22, 0x66000000);
            graphics.drawCenteredString(font, Component.literal(stage), width / 2, 8, 0xD9F1FF);
            if (clientTicks >= countdownTicks + liftoffTicks && clientTicks < countdownTicks + liftoffTicks + ascentTicks) {
                graphics.drawCenteredString(font, Component.literal("Look around."), width / 2, 20, 0x91C9FF);
            }
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

    private static String getStageLabel() {
        if (clientTicks < countdownTicks + liftoffTicks) {
            return "LIFTOFF";
        }
        if (clientTicks < countdownTicks + liftoffTicks + ascentTicks) {
            return "ASCENT";
        }
        if (clientTicks < countdownTicks + liftoffTicks + ascentTicks + atmosphereExitTicks) {
            return "ATMOSPHERE EXIT";
        }
        return "BLACKOUT";
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
