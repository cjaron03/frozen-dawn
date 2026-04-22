package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.registries.DeferredHolder;

final class BlackglassAudioPlayer {

    private static SoundInstance currentSound;
    private static int currentSegment = -1;

    private BlackglassAudioPlayer() {
    }

    static void playSegment(int segmentIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null || segmentIndex < 0 || segmentIndex >= ModSounds.BLACKGLASS_SEGMENTS.size()) {
            return;
        }

        stop();

        DeferredHolder<SoundEvent, SoundEvent> holder = ModSounds.BLACKGLASS_SEGMENTS.get(segmentIndex);
        ResourceLocation location = holder.get().getLocation();
        currentSound = new SimpleSoundInstance(
                location,
                SoundSource.RECORDS,
                1.15F,
                1.0F,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        currentSegment = segmentIndex;
        mc.getSoundManager().play(currentSound);
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (currentSound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(currentSound);
        }
        currentSound = null;
        currentSegment = -1;
    }

    static boolean isPlaying(int segmentIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (currentSound == null || currentSegment != segmentIndex || mc.getSoundManager() == null) {
            return false;
        }
        boolean active = mc.getSoundManager().isActive(currentSound);
        if (!active) {
            currentSound = null;
            currentSegment = -1;
        }
        return active;
    }
}
