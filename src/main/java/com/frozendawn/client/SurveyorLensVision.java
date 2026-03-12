package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.SurveyorLensScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final int SCAN_INTERVAL = 8;

    private static final List<SurveyorLensScanner.HeatSignature> cachedSignatures = new ArrayList<>();
    private static float overlayStrength = 0.0F;

    private SurveyorLensVision() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            fadeOut();
            return;
        }

        SurveyorLensScanner.LensProfile activeProfile = SurveyorLensScanner.passiveProfile(
                mc.player.getMainHandItem(),
                mc.player.getOffhandItem(),
                mc.player.getItemBySlot(EquipmentSlot.HEAD)
        );

        if (activeProfile == null) {
            cachedSignatures.clear();
            fadeOut();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);

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
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }
}
