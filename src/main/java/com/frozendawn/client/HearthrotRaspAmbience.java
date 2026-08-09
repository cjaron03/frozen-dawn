package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.event.MobFreezeHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Crystalline breathing layer mixed beneath, never replacing, EVA breathing. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HearthrotRaspAmbience {
    private static final int CLIP_TICKS = 160;
    private static TickableWindSound sound;
    private static int restartTicks;

    private HearthrotRaspAmbience() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()
                || HearthrotClientState.stage() < 2
                || MobFreezeHandler.getFullSetTier(minecraft.player) != 3) {
            stop();
            return;
        }
        float targetVolume = Math.min(
                0.30F, 0.08F + HearthrotClientState.stage() * 0.032F)
                * HearthrotClientState.breathingVolumeMultiplier();
        if (sound != null && !sound.isStopped()) {
            sound.setTargetVolume(
                    targetVolume,
                    HearthrotClientState.breathingVolumeMultiplier() < 1.0F
                            ? 0.10F : 0.035F);
        }
        if (restartTicks > 0 && --restartTicks > 0) {
            return;
        }
        sound = new TickableWindSound(
                ModSounds.HEARTHROT_RASP.get(), targetVolume, 1.0F, CLIP_TICKS);
        minecraft.getSoundManager().play(sound);
        restartTicks = CLIP_TICKS - 20;
    }

    private static void stop() {
        if (sound != null) {
            sound.fadeOut();
            sound = null;
        }
        restartTicks = 0;
    }
}
