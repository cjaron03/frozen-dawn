package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Modifies fog color and distance based on apocalypse progression.
 * - Sky darkens as skyLight decreases (all phases)
 * - Fog closes in during phases 3+ (visibility 256 → 48 blocks)
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SkyRenderer {

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FrozenDawnConfig.ENABLE_SKY_DARKENING.get()) return;

        float skyLight = ApocalypseClientData.getSkyLight();
        if (skyLight >= 1f) return;

        event.setRed(event.getRed() * skyLight);
        event.setGreen(event.getGreen() * skyLight);
        event.setBlue(event.getBlue() * skyLight);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        float progress = ApocalypseClientData.getProgress();
        // Phase 3 starts at progress 0.35; fog closes from 256 → 48 blocks by phase 5 end
        float fogProgress = Math.min(1f, (progress - 0.35f) / 0.65f);
        float visibility = Mth.lerp(fogProgress, 256f, 48f);

        float currentFar = event.getFarPlaneDistance();
        if (visibility < currentFar) {
            event.setFarPlaneDistance(visibility);
            event.setNearPlaneDistance(visibility * 0.05f);
            event.setCanceled(true);
        }
    }
}
