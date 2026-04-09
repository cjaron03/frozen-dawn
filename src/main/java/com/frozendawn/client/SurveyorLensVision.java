package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.mixin.GameRendererAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final int SCAN_INTERVAL = 8;
    private static final int MAX_SHADER_FIELDS = 6;
    private static final int MAX_COLD_FIELDS = 24;
    private static final int THERMAL_BOOT_TICKS = 42;
    private static final float THERMAL_FADE_IN_STEP = 0.035F;
    private static final float THERMAL_FADE_OUT_STEP = 0.028F;
    private static final ResourceLocation THERMAL_POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shaders/post/orsa_thermal_v8a.json");
    private static final KeyMapping THERMAL_MODE_KEY = new KeyMapping(
            "key.frozendawn.toggle_thermal_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.frozendawn"
    );

    private static final List<SurveyorLensScanner.HeatSignature> cachedSignatures = new ArrayList<>();
    private static final List<SurveyorLensTargetCollector.ColdAnchor> cachedColdAnchors = new ArrayList<>();
    private static float overlayStrength = 0.0F;
    private static float thermalModeStrength = 0.0F;
    private static boolean thermalModeEnabled = false;
    private static int thermalBootTicksRemaining = 0;
    private static int thermalShutdownTicksRemaining = 0;

    private SurveyorLensVision() {
    }

    public static KeyMapping thermalModeKey() {
        return THERMAL_MODE_KEY;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        ItemStack headArmor = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean visorEquipped = headArmor.is(ModItems.ORSA_THERMAL_VISOR.get());
        SurveyorLensScanner.LensProfile heldProfile = SurveyorLensScanner.heldProfile(
                mc.player.getMainHandItem(),
                mc.player.getOffhandItem()
        );
        while (THERMAL_MODE_KEY.consumeClick()) {
            if (visorEquipped) {
                thermalModeEnabled = !thermalModeEnabled;
                if (thermalModeEnabled) {
                    thermalBootTicksRemaining = THERMAL_BOOT_TICKS;
                    thermalShutdownTicksRemaining = 0;
                } else {
                    thermalBootTicksRemaining = 0;
                    thermalShutdownTicksRemaining = THERMAL_BOOT_TICKS;
                }
            }
        }

        boolean visorThermalActive = visorEquipped && (
                thermalModeEnabled
                        || thermalBootTicksRemaining > 0
                        || thermalShutdownTicksRemaining > 0
                        || thermalModeStrength > 0.01F
        );
        SurveyorLensScanner.LensProfile activeProfile = visorThermalActive
                ? SurveyorLensScanner.LensProfile.VISOR
                : heldProfile;

        if (activeProfile == null) {
            cachedSignatures.clear();
            cachedColdAnchors.clear();
            if (!visorEquipped) {
                thermalModeEnabled = false;
                thermalBootTicksRemaining = 0;
                thermalShutdownTicksRemaining = 0;
            }
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);
        if (!visorEquipped) {
            thermalModeEnabled = false;
            thermalBootTicksRemaining = 0;
            thermalShutdownTicksRemaining = 0;
        }

        if (thermalModeEnabled) {
            thermalModeStrength = Math.min(1.0F, thermalModeStrength + THERMAL_FADE_IN_STEP);
            if (thermalBootTicksRemaining > 0) {
                thermalBootTicksRemaining--;
            }
            thermalShutdownTicksRemaining = 0;
        } else if (thermalShutdownTicksRemaining > 0) {
            thermalModeStrength = Math.max(0.0F, thermalModeStrength - (THERMAL_FADE_OUT_STEP * 0.78F));
            thermalShutdownTicksRemaining--;
        } else {
            fadeThermal();
        }

        syncThermalPostEffect(mc, thermalModeStrength > 0.01F);

        long gameTime = mc.level.getGameTime();
        if (gameTime % SCAN_INTERVAL != 0) {
            return;
        }

        cachedSignatures.clear();
        cachedSignatures.addAll(SurveyorLensScanner.collectHeatSignatures(
                mc.level,
                mc.player.position(),
                mc.player.blockPosition(),
                activeProfile
        ));
        cachedColdAnchors.clear();
        if (visorEquipped) {
            cachedColdAnchors.addAll(SurveyorLensTargetCollector.collectColdAnchors(mc, MAX_COLD_FIELDS));
        }

        int markers = Math.min(activeProfile.maxMarkers(), cachedSignatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            if (activeProfile != SurveyorLensScanner.LensProfile.VISOR && thermalModeStrength <= 0.05F) {
                mc.level.addParticle(signature.sourceType().markerParticle(), x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderThermalSignatures(event);
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    public static boolean isThermalModeVisible() {
        return thermalModeStrength > 0.01F || thermalBootTicksRemaining > 0 || thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalModeStrength() {
        return easedThermalStrength();
    }

    public static boolean isThermalBooting() {
        return thermalBootTicksRemaining > 0;
    }

    public static boolean isThermalShuttingDown() {
        return thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalBootProgress() {
        if (!isThermalBooting()) {
            return 0.0F;
        }
        return 1.0F - (thermalBootTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static float getThermalShutdownProgress() {
        if (!isThermalShuttingDown()) {
            return 0.0F;
        }
        return 1.0F - (thermalShutdownTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static List<SurveyorLensScanner.HeatSignature> getCachedSignatures() {
        return Collections.unmodifiableList(cachedSignatures);
    }

    public static void syncThermalShaderUniforms(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        if (currentEffect == null || !THERMAL_POST_EFFECT.toString().equals(currentEffect.getName())) {
            return;
        }

        currentEffect.setUniform("ThermalAmount", easedThermalStrength());
        currentEffect.setUniform("AmbientBaseline", SurveyorLensProjectionMath.ambientBaselineFromTemp(TemperatureHud.getDisplayedTemp()));
        SurveyorLensRenderPass.clearThermalShaderFields(currentEffect, MAX_SHADER_FIELDS, MAX_COLD_FIELDS);
    }

    private static void syncThermalPostEffect(Minecraft mc, boolean shouldEnable) {
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        boolean thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());

        if (shouldEnable) {
            if (!thermalEffectActive) {
                mc.gameRenderer.loadEffect(THERMAL_POST_EFFECT);
                currentEffect = accessor.frozendawn$getPostEffect();
                thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());
            }

            if (thermalEffectActive && currentEffect != null) {
                currentEffect.setUniform("ThermalAmount", easedThermalStrength());
                SurveyorLensRenderPass.clearThermalShaderFields(currentEffect, MAX_SHADER_FIELDS, MAX_COLD_FIELDS);
            }
            return;
        }

        if (thermalEffectActive) {
            accessor.frozendawn$shutdownEffect();
        }
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }

    private static void fadeThermal() {
        thermalModeStrength = Math.max(0.0F, thermalModeStrength - THERMAL_FADE_OUT_STEP);
    }

    private static float easedThermalStrength() {
        float t = Mth.clamp(thermalModeStrength, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void renderThermalSignatures(RenderLevelStageEvent event) {
        if (!isThermalModeVisible()) {
            return;
        }
        SurveyorLensRenderPass.renderThermalSignatures(
                event,
                cachedSignatures,
                cachedColdAnchors,
                thermalModeStrength,
                MAX_SHADER_FIELDS,
                MAX_COLD_FIELDS
        );
    }
}
