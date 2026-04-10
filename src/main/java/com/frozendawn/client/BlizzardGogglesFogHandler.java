package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.BlizzardGogglesHandler;
import com.frozendawn.vision.VisionMode;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class BlizzardGogglesFogHandler {

    private BlizzardGogglesFogHandler() {
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (SurveyorLensVision.getActiveVisionMode() != VisionMode.BLIZZARD
                || event.getType() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }

        if (event.getFarPlaneDistance() >= BlizzardGogglesHandler.BLIZZARD_FOG_DISTANCE_BLOCKS) {
            return;
        }

        event.setFarPlaneDistance(BlizzardGogglesHandler.BLIZZARD_FOG_DISTANCE_BLOCKS);
        event.setCanceled(true);
    }
}
