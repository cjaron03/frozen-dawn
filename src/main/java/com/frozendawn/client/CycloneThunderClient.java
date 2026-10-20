package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.cyclone.*;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded local cues derived from the synced field; no lightning entity or world mutation. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class CycloneThunderClient {
    private static final CycloneThunder.Schedule SCHEDULE = new CycloneThunder.Schedule();
    private static ClientLevel previousLevel;
    private static final java.util.List<ThunderRoll> ROLLS = new java.util.ArrayList<>();
    private static long clientTicks;
    private static int cueCount;
    private static CycloneThunder.Band lastBand = CycloneThunder.Band.NONE;
    public static int cueCount() { return cueCount; }
    public static String lastBand() { return lastBand.name(); }
    public static int activeRolls() { return ROLLS.size(); }
    private static int flashTicks, frontTicks;
    private static float frontCenter;
    private CycloneThunderClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != previousLevel) { clear(); previousLevel = mc.level; }
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (mc.isPaused()) return;
        clientTicks++;
        // Sound filters can wrap the instance. Own expiry and a delegated stopped flag
        // keep cleanup reliable even when the engine no longer indexes our original instance.
        ROLLS.removeIf(sound -> {
            if (sound.expiresAt <= mc.level.getGameTime() || clientTicks - sound.lastTick > 5) sound.stopNow();
            return sound.isStopped();
        });
        if (flashTicks > 0) flashTicks--;
        if (frontTicks > 0) frontTicks--;
        var storm = CycloneClientState.storm().orElse(null);
        var sample = CycloneClientState.sampleLocal(0);
        float surface = CycloneThermal.surfaceWeight(mc.player.getY());
        float trackWeight = storm == null ? 0 : (float) Math.clamp(
                (CycloneField.HALF_WIDTH - Math.abs(CycloneField.cross(storm, mc.player.getX(), mc.player.getZ())))
                        / CycloneField.EDGE_FADE, 0, 1);
        float gain = CycloneThunder.gain(sample.stage(), sample.intensity(), trackWeight, surface,
                StormExposureController.getExposure());
        for (var roll : ROLLS) roll.gain(gain);
        var cue = SCHEDULE.tick(storm, sample, (long) CycloneClientState.time(0), gain > .001f);
        if (storm == null || cue == CycloneThunder.Cue.NONE && gain <= .001f) { stopPresentation(); return; }
        if (cue.band() == CycloneThunder.Band.NONE) return;
        // Finite close/mid/distant clips overlap naturally within the ten-second expiry.
        // Six slots cover even cold-filtered slow playback at the two-second minimum interval.
        if (ROLLS.size() >= 6) return;
        var sound = switch (cue.band()) {
            case CLOSE -> ModSounds.CYCLONE_THUNDER_CLOSE.get();
            case MID -> ModSounds.CYCLONE_THUNDER_MID.get();
            default -> ModSounds.CYCLONE_THUNDER_DISTANT.get();
        };
        float volume = switch (cue.band()) { case CLOSE -> 1.65f; case MID -> 1.15f; default -> .85f; };
        // Relative WEATHER sound honors the weather slider without positional distance loss or mob masking.
        var playing = new ThunderRoll(sound, volume, gain, .94f + mc.level.random.nextFloat() * .10f,
                mc.level.getGameTime() + 200);
        mc.getSoundManager().play(playing);
        ROLLS.add(playing); cueCount++; lastBand = cue.band();
        if (flashScale() <= 0) return;
        if (cue.frontFlash()) {
            frontTicks = 5;
            frontCenter = mc.level.random.nextFloat() * 1.4f - .7f;
        } else if (cue.band() == CycloneThunder.Band.CLOSE && StormExposureController.getExposure() > .1f) {
            flashTicks = 2;
            // Reduced mode uses scaled atmosphere only; full mode includes vanilla world lighting.
            if (flashScale() >= .999f) mc.level.setSkyFlashTime(2);
        }
    }

    public static float flashScale() {
        return Minecraft.getInstance().options.hideLightningFlash().get() ? 0
                : FrozenDawnConfig.CYCLONE_FLASH_INTENSITY.get().floatValue();
    }
    public static float worldFlash(float partial) {
        return Math.max(0, flashTicks - partial) / 2f * flashScale() * StormExposureController.getExposure();
    }
    public static float frontFlash(double u, double v) {
        if (frontTicks <= 0) return 0;
        double d = (u - frontCenter) / .22;
        return (float) (frontTicks / 5f * flashScale() * Math.exp(-d * d)
                * Math.clamp((v - .15) / .3, 0, 1) * Math.clamp((.95 - v) / .25, 0, 1));
    }
    private static void stopPresentation() {
        for (var sound : ROLLS) sound.stopNow();
        ROLLS.clear(); flashTicks = frontTicks = 0;
    }
    private static final class ThunderRoll extends AbstractTickableSoundInstance {
        final long expiresAt;
        private final float baseVolume;
        private long lastTick = clientTicks;
        ThunderRoll(SoundEvent sound, float baseVolume, float gain, float pitch, long expiresAt) {
            super(sound, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.expiresAt = expiresAt; this.baseVolume = baseVolume;
            volume = baseVolume * gain; this.pitch = pitch; relative = true; attenuation = Attenuation.NONE;
            looping = false; delay = 0;
        }
        void gain(float weight) { volume = baseVolume * weight; }
        @Override public void tick() { lastTick = clientTicks; }
        void stopNow() { stop(); }
    }
    private static void clear() { SCHEDULE.clear(); stopPresentation(); cueCount = 0; clientTicks = 0; lastBand = CycloneThunder.Band.NONE; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); previousLevel = null; }
}
