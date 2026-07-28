package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.MasterArchitectWeatherPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
    private static int auraTier;
    private static boolean fightActive;
    private static float visualAuraTier;
    private static BlockPos hearthCenter = BlockPos.ZERO;
    private static boolean anchored;
    private static boolean suppressedForDeath;

    private MasterArchitectWeather() {
    }

    public static void update(MasterArchitectWeatherPayload payload) {
        MasterArchitectAuraClient.updateAftermath(payload);
        if (payload.hearthStormDead()) {
            suppressAfterMasterDeath();
            return;
        }
        boolean initialAnchor = payload.anchored()
                && (!anchored || !hearthCenter.equals(payload.hearthCenter()));
        suppressedForDeath = false;
        targetStrength = Mth.clamp(payload.strength(), 0.0F, 1.0F);
        auraTier = Mth.clamp(payload.auraTier(), 0, 3);
        fightActive = payload.fightActive();
        hearthCenter = payload.hearthCenter().immutable();
        anchored = payload.anchored();
        if (initialAnchor) {
            visualAuraTier = auraTier;
        }
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
        float auraStep = 1.0F / Math.max(
                1.0F,
                com.frozendawn.config.FrozenDawnConfig
                        .MASTER_AURA_STORM_RESPONSE_SECONDS.get().floatValue() * 20.0F);
        visualAuraTier = moveToward(
                visualAuraTier, auraTier, auraStep);
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

    public static int getAuraTier() {
        return auraTier;
    }

    public static boolean isFightActive() {
        return fightActive;
    }

    public static float getVisualAuraTier() {
        return visualAuraTier;
    }

    public static BlockPos getHearthCenter() {
        return hearthCenter;
    }

    public static boolean hasAuraAnchor() {
        return anchored && auraTier > 0;
    }

    public static float getAuraProximity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasAuraAnchor() || minecraft.player == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(minecraft.player.blockPosition()
                .distSqr(hearthCenter));
        double radius = com.frozendawn.config.FrozenDawnConfig
                .MASTER_AURA_RADIUS.get();
        return Mth.clamp((float) (1.0D - distance / radius), 0.0F, 1.0F);
    }

    static boolean isRequested() {
        return targetStrength > ACTIVE_THRESHOLD || currentStrength > ACTIVE_THRESHOLD;
    }

    static void suppressAfterMasterDeath() {
        targetStrength = 0.0F;
        currentStrength = 0.0F;
        auraTier = 0;
        fightActive = false;
        visualAuraTier = 0.0F;
        anchored = false;
        suppressedForDeath = true;
    }

    static void reset() {
        targetStrength = 0.0F;
        currentStrength = 0.0F;
        auraTier = 0;
        fightActive = false;
        visualAuraTier = 0.0F;
        hearthCenter = BlockPos.ZERO;
        anchored = false;
        suppressedForDeath = false;
    }

    private static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
