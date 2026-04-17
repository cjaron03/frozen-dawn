package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EndingMusicController {
    private static final int FADE_START_TICKS = 8 * 60 * 20;
    private static final int FADE_END_TICKS = 9 * 60 * 20 + 30 * 20;
    private static EndingMusicSound currentSound;
    private static int manualFadeTicks;
    private static int manualFadeTotalTicks;

    private EndingMusicController() {
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        stop();
        try {
            currentSound = new EndingMusicSound();
            manualFadeTicks = 0;
            manualFadeTotalTicks = 0;
            mc.getSoundManager().play(currentSound);
        } catch (RuntimeException ignored) {
            currentSound = null;
        }
    }

    public static void tick(int endingTicks) {
        if (currentSound == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getSoundManager().isActive(currentSound)) {
            currentSound = null;
            return;
        }
        if (manualFadeTicks > 0) {
            currentSound.setManualFadeVolume(manualFadeTicks / (float) Math.max(1, manualFadeTotalTicks));
            manualFadeTicks--;
            if (manualFadeTicks <= 0) {
                stop();
            }
            return;
        }
        currentSound.setVolumeForEndingTick(endingTicks);
    }

    public static void fadeOutAndStop(int fadeTicks) {
        if (currentSound == null) {
            return;
        }
        manualFadeTicks = Math.max(1, fadeTicks);
        manualFadeTotalTicks = manualFadeTicks;
    }

    public static void stop() {
        if (currentSound != null) {
            Minecraft.getInstance().getSoundManager().stop(currentSound);
            currentSound = null;
        }
        manualFadeTicks = 0;
        manualFadeTotalTicks = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    private static final class EndingMusicSound extends AbstractTickableSoundInstance {
        private EndingMusicSound() {
            super(SoundEvents.MUSIC_END.value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        private void setVolumeForEndingTick(int endingTicks) {
            if (endingTicks < FADE_START_TICKS) {
                this.volume = 1.0F;
                return;
            }
            float fade = (endingTicks - FADE_START_TICKS) / (float) Math.max(1, FADE_END_TICKS - FADE_START_TICKS);
            this.volume = 1.0F - Mth.clamp(fade, 0.0F, 1.0F);
            if (this.volume <= 0.001F && endingTicks >= FADE_END_TICKS) {
                EndingMusicController.stop();
            }
        }

        private void setManualFadeVolume(float fadeMultiplier) {
            this.volume = Mth.clamp(fadeMultiplier, 0.0F, 1.0F);
        }

        @Override
        public void tick() {
        }
    }
}
