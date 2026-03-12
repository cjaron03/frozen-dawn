package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.SurveyorLensScanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final int SCAN_INTERVAL = 8;
    private static final int THERMAL_BOOT_TICKS = 36;
    private static final float THERMAL_FADE_STEP = 0.06F;
    private static final KeyMapping THERMAL_MODE_KEY = new KeyMapping(
            "key.frozendawn.toggle_thermal_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.frozendawn"
    );

    private static final List<SurveyorLensScanner.HeatSignature> cachedSignatures = new ArrayList<>();
    private static float overlayStrength = 0.0F;
    private static float thermalModeStrength = 0.0F;
    private static boolean thermalModeEnabled = false;
    private static int thermalBootTicksRemaining = 0;

    private SurveyorLensVision() {}

    public static KeyMapping thermalModeKey() {
        return THERMAL_MODE_KEY;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            fadeOut();
            fadeThermal();
            return;
        }

        ItemStack headArmor = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean visorEquipped = headArmor.is(ModItems.ORSA_THERMAL_VISOR.get());
        while (THERMAL_MODE_KEY.consumeClick()) {
            if (visorEquipped) {
                thermalModeEnabled = !thermalModeEnabled;
                thermalBootTicksRemaining = thermalModeEnabled ? THERMAL_BOOT_TICKS : 0;
            }
        }

        SurveyorLensScanner.LensProfile activeProfile = SurveyorLensScanner.passiveProfile(
                mc.player.getMainHandItem(),
                mc.player.getOffhandItem(),
                headArmor
        );

        if (activeProfile == null) {
            cachedSignatures.clear();
            thermalModeEnabled = false;
            thermalBootTicksRemaining = 0;
            fadeOut();
            fadeThermal();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);
        if (!visorEquipped) {
            thermalModeEnabled = false;
            thermalBootTicksRemaining = 0;
        }

        if (thermalModeEnabled) {
            thermalModeStrength = Math.min(1.0F, thermalModeStrength + THERMAL_FADE_STEP);
            if (thermalBootTicksRemaining > 0) {
                thermalBootTicksRemaining--;
            }
        } else {
            fadeThermal();
        }

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

        int markers = Math.min(activeProfile.maxMarkers(), cachedSignatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            mc.level.addParticle(signature.sourceType().markerParticle(), x, y, z, 0.0D, 0.01D, 0.0D);
            if (thermalModeStrength > 0.05F) {
                mc.level.addParticle(signature.sourceType().markerParticle(), x + 0.11D, y + 0.02D, z - 0.08D, 0.0D, 0.02D, 0.0D);
                mc.level.addParticle(signature.sourceType().markerParticle(), x - 0.09D, y - 0.01D, z + 0.10D, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    public static boolean isThermalModeVisible() {
        return thermalModeStrength > 0.01F || thermalBootTicksRemaining > 0;
    }

    public static float getThermalModeStrength() {
        return thermalModeStrength;
    }

    public static boolean isThermalBooting() {
        return thermalBootTicksRemaining > 0;
    }

    public static float getThermalBootProgress() {
        if (!thermalModeEnabled) {
            return 0.0F;
        }
        return 1.0F - (thermalBootTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static List<SurveyorLensScanner.HeatSignature> getCachedSignatures() {
        return Collections.unmodifiableList(cachedSignatures);
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }

    private static void fadeThermal() {
        thermalModeStrength = Math.max(0.0F, thermalModeStrength - THERMAL_FADE_STEP);
    }
}
