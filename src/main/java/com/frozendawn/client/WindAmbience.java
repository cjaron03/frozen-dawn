package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays long (~65-70s) ambient wind clips with overlapping crossfade.
 * Uses TickableWindSound for smooth per-frame volume transitions (no hard cuts).
 * Next clip starts 5s before the current one ends for seamless overlap.
 *
 * Phase 6 early: maximum volume (1.0). Mid: wind dies down. Late: silence.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WindAmbience {

    private static final int LIGHT_DURATION = 1140; // 57s in ticks
    private static final int STRONG_DURATION = 1260; // 63s in ticks
    private static final int OVERLAP = 100;          // 5s overlap (matches file fade-out)

    private static TickableWindSound currentSound = null;
    private static int ticksUntilNext = 0;
    private static int creakCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        boolean underground = mc.player.blockPosition().getY() < 50;

        // Phase 6 late: no wind (vacuum)
        boolean shouldStop = phase < 3 || underground || (phase >= 6 && progress >= 0.85f);
        if (shouldStop) {
            stopAll(mc);
            return;
        }

        float targetVolume;
        if (phase >= 6) {
            if (progress <= 0.72f) {
                targetVolume = 1.0f;
            } else {
                float fadeProgress = Math.min(1f, (progress - 0.72f) / 0.13f);
                targetVolume = Mth.lerp(fadeProgress, 1.0f, 0.0f);
            }
        } else {
            targetVolume = switch (phase) {
                case 3 -> 0.2f;
                case 4 -> 0.45f;
                default -> 0.85f;
            };
        }

        // Muffle wind when sheltered (roof overhead)
        boolean sheltered = isSheltered(mc);
        if (sheltered) {
            targetVolume *= 0.25f;
        }

        // Update volume on the currently playing sound — it fades smoothly per-frame
        if (currentSound != null && !currentSound.isStopped()) {
            currentSound.setTargetVolume(targetVolume);
        }

        // If target is near-zero and no sound playing, bail
        if (targetVolume < 0.01f && (currentSound == null || currentSound.isStopped())) {
            stopAll(mc);
            return;
        }

        // Occasional creaking when sheltered in phase 4+ (structure stress from wind/snow)
        if (sheltered && phase >= 4) {
            if (creakCooldown > 0) {
                creakCooldown--;
            } else if (mc.level.random.nextFloat() < 0.015f) {
                float pitch = 0.7f + mc.level.random.nextFloat() * 0.4f;
                float vol = 0.3f + mc.level.random.nextFloat() * 0.2f;
                mc.level.playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        ModSounds.SHELTER_CREAK.get(), SoundSource.AMBIENT,
                        vol, pitch, false);
                creakCooldown = 80 + mc.level.random.nextInt(160); // 4-12s between creaks
            }
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one still playing for 5s overlap
        boolean strong = phase >= 4;
        float pitch = 0.97f + mc.level.random.nextFloat() * 0.06f;
        int clipDuration = strong ? STRONG_DURATION : LIGHT_DURATION;

        currentSound = new TickableWindSound(
                strong ? ModSounds.WIND_STRONG.get() : ModSounds.WIND_LIGHT.get(),
                targetVolume, pitch, clipDuration);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = clipDuration - OVERLAP;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        stopAll(mc);
        ApocalypseClientData.reset();
        TemperatureHud.reset();
    }

    /** Check if the player has a solid block or insulated glass overhead (within 4 blocks). */
    private static boolean isSheltered(Minecraft mc) {
        return TemperatureManager.isSheltered(mc.level, mc.player.blockPosition());
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        ticksUntilNext = 0;
        creakCooldown = 0;
    }
}
