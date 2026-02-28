package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.MobFreezeHandler;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays EVA suit breathing ambience in phase 6 late (vacuum).
 * Also plays a suffocation gasp when any EVA piece is removed in vacuum.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class EvaSuitAmbience {

    private static final int CLIP_DURATION = 300;  // 15s in ticks (matches the ogg length)
    private static final int OVERLAP = 40;          // 2s overlap for seamless loop
    private static final float TARGET_VOLUME = 0.5f;

    private static TickableWindSound currentSound = null;
    private static SimpleSoundInstance suffocateSound = null;
    private static int ticksUntilNext = 0;
    private static boolean wasFullEva = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            stopAll(mc);
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();

        boolean inVacuum = phase >= 6 && progress >= 0.85f;
        boolean fullEva = MobFreezeHandler.getFullSetTier(mc.player) == 3;
        boolean exposedToSurface = !TemperatureManager.isEnclosed(mc.level, mc.player.blockPosition());

        // Detect suit removal in vacuum on surface — play suffocation gasp
        if (inVacuum && exposedToSurface && wasFullEva && !fullEva) {
            suffocateSound = SimpleSoundInstance.forUI(
                    ModSounds.EVA_SUFFOCATE.get(), 1.0f, 0.8f);
            mc.getSoundManager().play(suffocateSound);
        }
        // Stop suffocation sound when suit is back on
        if (fullEva && suffocateSound != null) {
            mc.getSoundManager().stop(suffocateSound);
            suffocateSound = null;
        }
        wasFullEva = fullEva;

        boolean shouldPlay = fullEva && inVacuum && exposedToSurface;

        if (!shouldPlay) {
            stopAll(mc);
            return;
        }

        // Update volume on current sound
        if (currentSound != null && !currentSound.isStopped()) {
            currentSound.setTargetVolume(TARGET_VOLUME);
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one may still be playing for overlap
        float pitch = 0.98f + mc.level.random.nextFloat() * 0.04f;
        currentSound = new TickableWindSound(
                ModSounds.EVA_BREATHING.get(),
                TARGET_VOLUME, pitch, CLIP_DURATION);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = CLIP_DURATION - OVERLAP;
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            currentSound.fadeOut();
            currentSound = null;
        }
        ticksUntilNext = 0;
    }
}
