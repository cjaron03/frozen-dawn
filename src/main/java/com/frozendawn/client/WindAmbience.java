package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.phase.PhaseManager;
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
 * Keeps every overlapping wind voice under one owner. Recorded cyclone beds use six-second
 * equal-power overlaps; stage and shelter changes update both voices without restarting clips.
 * Ordinary and post-Maeve source files remain unchanged, with their existing five-second overlaps.
 *
 * Phase 6 early: maximum volume (1.0). Mid: wind dies down. Late: silence,
 * except inside a living Master Architect's local Hearth storm.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WindAmbience {

    private static final int LIGHT_DURATION = 1140; // 57s in ticks
    private static final int STRONG_DURATION = 1260; // 63s in ticks
    private static final int OVERLAP = 100;          // 5s overlap (matches file fade-out)

    private static final WindBedLayer normalBed=new WindBedLayer();
    private static final WindBedLayer snowBed=new WindBedLayer();
    private static final WindBedLayer roarBed=new WindBedLayer();
    private static final WindBedLayer roomBed=new WindBedLayer();
    private static final WindBedLayer buffetBed=new WindBedLayer();
    private static com.frozendawn.cyclone.WindAudioPolicy.Mix cycloneMix=new com.frozendawn.cyclone.WindAudioPolicy.Mix();
    private static com.frozendawn.cyclone.CycloneSeverity lastSeverity=com.frozendawn.cyclone.CycloneSeverity.MODERATE;
    private static int creakCooldown;
    private static float mobMaskingStrength;
    static float mobMaskingStrength(){return mobMaskingStrength;}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) { stopAll(mc); return; }
        if (mc.isPaused()) return;

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        boolean underground = mc.player.blockPosition().getY() < 50;
        float masterStrength = MasterArchitectWeather.getStrength();
        boolean postMaeve = PostMaeveClientState.isMaeveErased();

        boolean audible=!shouldStopWind(phase,progress,underground,masterStrength,postMaeve);
        var sample=CycloneClientState.sampleLocal(0);
        CycloneClientState.storm().ifPresent(storm->lastSeverity=storm.severity());
        cycloneMix.tick(sample,lastSeverity,StormExposureController.getExposure());

        float targetVolume = postMaeve
                ? FrozenDawnConfig.POST_MAEVE_AMBIENT_VOLUME_MULTIPLIER.get().floatValue()
                : Math.max(getTargetWindVolume(phase, progress),
                        0.9F * masterStrength);
        targetVolume *= cycloneMix.normal();

        float exposure = StormExposureController.getExposure();
        boolean exposedToStorm = ClientStormVisibility.isStormExposed(mc);
        targetVolume *= Mth.lerp(exposure, 0.08f, 1.0f);
        targetVolume *= MasterArchitectFourthWallMoment.weatherAudioMultiplier();
        targetVolume *= MasterArchitectSeverTelegraph.weatherAudioMultiplier();
        float heartQuiet = HeartQuietClient.environmentMultiplier();
        targetVolume *= heartQuiet;
        targetVolume *= BloomClientState.windMultiplier();
        if (!audible) targetVolume=0;
        boolean strong=phase>=4 || masterStrength>.05f;
        var normal=postMaeve?(strong?ModSounds.WIND_POST_MAEVE_STRONG.get():ModSounds.WIND_POST_MAEVE_LIGHT.get())
                :(strong?ModSounds.WIND_STRONG.get():ModSounds.WIND_LIGHT.get());
        // Existing beds carry their own edge fades. Fixed pitch keeps their measured duration exact.
        normalBed.tick(mc,normal,targetVolume,strong?STRONG_DURATION:LIGHT_DURATION,OVERLAP,false);
        float environment=audible?heartQuiet*MasterArchitectFourthWallMoment.weatherAudioMultiplier()
                *MasterArchitectSeverTelegraph.weatherAudioMultiplier()*BloomClientState.windMultiplier():0;
        mobMaskingStrength=Math.clamp(cycloneMix.blend()*exposure*environment
                *mc.options.getSoundSourceVolume(SoundSource.AMBIENT),0,1);
        snowBed.tick(mc,ModSounds.CYCLONE_SNOW.get(),cycloneMix.snow()*environment,1280,120,true);
        roarBed.tick(mc,ModSounds.CYCLONE_ROAR.get(),cycloneMix.roar()*environment,1280,120,true);
        roomBed.tick(mc,ModSounds.CYCLONE_ROOM.get(),cycloneMix.room()*environment,1280,120,true);
        buffetBed.tick(mc,ModSounds.CYCLONE_BUFFET.get(),cycloneMix.buffet()*environment,1280,120,true);

        // Occasional creaking when sheltered in phase 4+ (structure stress from wind/snow)
        if (!exposedToStorm && phase >= 4 && heartQuiet > 0.15F) {
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

    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        stopAll(mc);
        ApocalypseClientData.reset();
        TemperatureHud.reset();
        AirStatusHud.reset();
        SanityClientData.reset();
        BloomClientState.reset();
    }

    private static void stopAll(Minecraft mc) {
        normalBed.stop(mc);snowBed.stop(mc);roarBed.stop(mc);roomBed.stop(mc);buffetBed.stop(mc);
        mobMaskingStrength=0;creakCooldown=0;cycloneMix=new com.frozendawn.cyclone.WindAudioPolicy.Mix();
        lastSeverity=com.frozendawn.cyclone.CycloneSeverity.MODERATE;
    }

    private static boolean shouldStopWind(
            int phase, float progress, boolean underground, float masterStrength,
            boolean postMaeve) {
        return underground || (!postMaeve && masterStrength <= 0.01F
                && (phase < 3 || PhaseManager.isVacuumActive(phase, progress)));
    }

    private static float getTargetWindVolume(int phase, float progress) {
        if (phase < 6) {
            return switch (phase) {
                case 3 -> 0.2f;
                case 4 -> 0.45f;
                default -> 0.85f;
            };
        }

        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 1.0f;
            case MID -> Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 1.0f, 0.0f);
            case VACUUM, INACTIVE -> 0.0f;
        };
    }
}
