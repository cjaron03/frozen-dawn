package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays long (~65-70s) ambient wind clips with overlapping crossfade.
 * Clips are internally pre-crossfaded so repetition sounds natural.
 * Next clip starts 5s before the current one ends for seamless overlap.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WindAmbience {

    private static final int LIGHT_DURATION = 1140; // 57s in ticks
    private static final int STRONG_DURATION = 1260; // 63s in ticks
    private static final int OVERLAP = 100;          // 5s overlap (matches file fade-out)

    private static SoundInstance currentSound = null;
    private static int ticksUntilNext = 0;
    private static float currentVolume = 0f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) {
            stopAll(mc);
            return;
        }

        float targetVolume = switch (phase) {
            case 3 -> 0.2f;
            case 4 -> 0.45f;
            default -> 0.85f;
        };

        // First play: jump to target so the clip isn't inaudibly quiet
        // (SimpleSoundInstance volume is fixed at creation)
        if (currentVolume == 0f) {
            currentVolume = targetVolume;
        } else if (currentVolume < targetVolume) {
            currentVolume = Math.min(targetVolume, currentVolume + 0.005f);
        } else if (currentVolume > targetVolume) {
            currentVolume = Math.max(targetVolume, currentVolume - 0.005f);
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip (old one still playing — they overlap for 5s)
        boolean strong = phase >= 4;
        float pitch = 0.97f + mc.level.random.nextFloat() * 0.06f;

        currentSound = new SimpleSoundInstance(
                strong ? ModSounds.WIND_STRONG.get().getLocation() : ModSounds.WIND_LIGHT.get().getLocation(),
                SoundSource.AMBIENT,
                currentVolume,
                pitch,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,
                true
        );
        mc.getSoundManager().play(currentSound);

        // Schedule next clip to start OVERLAP ticks before this one ends
        int clipDuration = strong ? STRONG_DURATION : LIGHT_DURATION;
        ticksUntilNext = clipDuration - OVERLAP;
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        ticksUntilNext = 0;
        currentVolume = 0f;
    }
}
