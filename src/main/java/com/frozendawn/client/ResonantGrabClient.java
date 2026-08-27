package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Small presentation-only sway. It never mutates player yaw or pitch. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ResonantGrabClient {
    private ResonantGrabClient() {
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.player.hasEffect(ModEffects.RESONANT_GRASP)
                || !FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get()) return;
        float time = minecraft.player.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 0.62F) * 1.15F);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 0.37F) * 0.32F);
    }
}
