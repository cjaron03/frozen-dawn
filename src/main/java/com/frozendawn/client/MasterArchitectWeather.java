package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.MasterArchitectWeatherPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Smooth client cache for the server-authoritative Major Hearth storm strength.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectWeather {
    private static final float ACTIVE_THRESHOLD = 0.01F;
    private static final float FADE_IN_STEP = 1.0F / 40.0F;
    private static final float FADE_OUT_STEP = 1.0F / 100.0F;

    private static float targetStrength;
    private static float currentStrength;

    private MasterArchitectWeather() {
    }

    public static void update(MasterArchitectWeatherPayload payload) {
        targetStrength = Mth.clamp(payload.strength(), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        float step = targetStrength > currentStrength
                ? FADE_IN_STEP : FADE_OUT_STEP;
        currentStrength = moveToward(currentStrength, targetStrength, step);
        if (currentStrength < ACTIVE_THRESHOLD && targetStrength <= 0.0F) {
            currentStrength = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static float getStrength() {
        return currentStrength;
    }

    public static float getExposedStrength() {
        return currentStrength * StormExposureController.getExposure();
    }

    static boolean isRequested() {
        return targetStrength > ACTIVE_THRESHOLD || currentStrength > ACTIVE_THRESHOLD;
    }

    static void reset() {
        targetStrength = 0.0F;
        currentStrength = 0.0F;
    }

    private static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
