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

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RocketLaunchClientController {
    private static boolean active;
    private static int entityId = -1;
    private static int countdownTicks;
    private static int ascentTicks;
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
        ascentTicks = payload.ascentTicks();
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

        if (rocket != null) {
            mc.setCameraEntity(rocket);
            if (clientTicks < countdownTicks) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            } else {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }

        if (clientTicks >= countdownTicks + ascentTicks + 20) {
            reset(mc);
        }
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
        if (clientTicks < countdownTicks) {
            int seconds = Mth.ceil((countdownTicks - clientTicks) / 20.0F);
            String countdown = "T-" + Math.max(0, seconds);
            graphics.fill(0, 0, width, 26, 0x88000000);
            graphics.drawCenteredString(font, Component.literal("LAUNCH SEQUENCE"), width / 2, 6, 0xD9F1FF);
            graphics.drawCenteredString(font, Component.literal(countdown), width / 2, 16, 0xFFF5C2);
        } else {
            String stage = clientTicks < countdownTicks + 60 ? "ASCENT" : "ATMOSPHERE EXIT";
            graphics.fill(0, 0, width, 22, 0x66000000);
            graphics.drawCenteredString(font, Component.literal(stage), width / 2, 8, 0xD9F1FF);
        }

        if (fade > 0.0F) {
            int alpha = Mth.clamp((int) (fade * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, alpha << 24);
        }
    }

    private static float getFadeAmount() {
        int fadeStart = countdownTicks + ascentTicks - 40;
        if (clientTicks <= fadeStart) {
            return 0.0F;
        }
        return Mth.clamp((clientTicks - fadeStart) / 40.0F, 0.0F, 1.0F);
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
        ascentTicks = 0;
        clientTicks = 0;
        storedCameraEntity = null;
    }
}
