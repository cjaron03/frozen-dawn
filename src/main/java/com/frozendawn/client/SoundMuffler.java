package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.init.ModSounds;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.world.ThaeIvenMindDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Muffles game sounds when temperature drops below -15C.
 * Simulates sound dampening from heavy snow and frozen air.
 * Volume decreases and pitch lowers as temperature drops.
 * Skips music, UI sounds, and our own wind ambience.
 *
 * Phase 6 late (vacuum): all carried sounds are cancelled.
 * Only music, UI, and in-suit EVA sounds survive.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SoundMuffler {

    private static final int BLOCK_BREAK_SOUND_WINDOW_TICKS = 20;
    private static final int BLOCK_BREAK_SOUND_LIMIT_PER_WINDOW = 24;
    private static int blockBreakSoundTicks;
    private static int blockBreakSoundsThisWindow;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SoundInstance original = event.getSound();
        if (original == null) return;
        ResourceLocation soundLocation = original.getLocation();
        if (shouldSuppressBlockBreakBurst(original, soundLocation)) {
            event.setSound(null);
            return;
        }
        if (OrsaAwakeningIntro.shouldSuppressNonIntroSound(soundLocation)) {
            event.setSound(null);
            return;
        }
        String soundPath = soundLocation.getPath();
        boolean isGeothermalCue = isGeothermalCue(soundLocation, soundPath);
        boolean isThaevenSound = ThaevenTransmissionOverlay.isTransmissionSound(soundLocation);
        boolean isSurveyorLensCue = isSurveyorLensCue(soundLocation, soundPath);
        boolean isWindAmbience = isWindAmbienceCue(soundLocation, soundPath);
        boolean isBloomSporeCue = isBloomSporeCue(soundLocation, soundPath);
        boolean isStillpointCue = soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)
                && soundPath.startsWith("block.stillpoint_core.");
        boolean isLocalHearthrotHurtCue = isLocalHearthrotHurtCue(
                mc, original, soundLocation, soundPath);

        if (shouldReplaceLocalPlayerHurt(
                mc, original, soundLocation, soundPath)) {
            event.setSound(SimpleSoundInstance.forUI(
                    randomHearthrotHurtSound(mc),
                    0.94F + mc.player.getRandom().nextFloat() * 0.12F,
                    1.15F));
            return;
        }

        // The Core's charge and formation are structural transmission cues. They
        // remain audible before the field creates an acoustic pocket of its own.
        if (isStillpointCue) {
            return;
        }

        if (FrozenDawnConfig.ENABLE_STILLPOINT_AUDIO_MUFFLING.get()
                && StillpointClientState.isListenerInside()) {
            SoundSource stillpointSource = original.getSource();
            boolean protectedSource = original.isRelative()
                    || stillpointSource == SoundSource.MASTER
                    || stillpointSource == SoundSource.MUSIC
                    || stillpointSource == SoundSource.VOICE;
            if (isWindAmbience) {
                event.setSound(new MuffledSound(original, 0.12F, 0.92F));
                return;
            }
            if (!protectedSource) {
                if (StillpointClientState.isOutsideSource(
                        original.getX(), original.getY(), original.getZ())) {
                    event.setSound(null);
                    return;
                }
                float volume = StillpointAudioFilter.useFallback() ? 0.54F : 0.76F;
                event.setSound(new MuffledSound(original, volume, 0.90F));
                // Sounds born inside the field form a muffled acoustic pocket. Returning
                // here keeps Phase 6 vacuum suppression from canceling them below.
                return;
            }
        }

        if (isThaevenSound || isSurveyorLensCue || isBloomSporeCue) {
            return;
        }

        float heartQuiet = HeartQuietClient.environmentMultiplier();
        boolean quietsWithHeart = original.getSource() == SoundSource.AMBIENT
                || original.getSource() == SoundSource.WEATHER
                || original.getSource() == SoundSource.HOSTILE
                || original.getSource() == SoundSource.NEUTRAL;
        boolean isHeartSound = soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)
                && (soundPath.startsWith("entity.thae_iven_heart.")
                || soundPath.startsWith("entity.heart_successor.")
                || soundPath.startsWith("music.thae_iven_heart."));
        if (quietsWithHeart && !isHeartSound && heartQuiet < 0.995F) {
            if (heartQuiet <= 0.01F) {
                event.setSound(null);
            } else {
                event.setSound(new MuffledSound(
                        original, heartQuiet, 0.96F + heartQuiet * 0.04F));
            }
            return;
        }

        if (MasterArchitectFloodClient.isActive()) {
            boolean isMasterFightMusic = soundLocation.getNamespace().equals(
                    FrozenDawn.MOD_ID)
                    && soundPath.startsWith("music.master_architect.");
            boolean isMasterArchitectSound = soundLocation.getNamespace().equals(
                    FrozenDawn.MOD_ID)
                    && soundPath.startsWith("entity.master_architect.");
            boolean isMasterArchitectUi = soundLocation.getNamespace().equals(
                    FrozenDawn.MOD_ID)
                    && soundPath.startsWith("ui.master_architect.");
            boolean isMindWitnessCue = ThaeIvenMindDimension.isMindLevel(mc.level)
                    && soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)
                    && soundPath.startsWith("ambient.sanity_");
            boolean isMindTransitionCue = soundLocation.getNamespace().equals("minecraft")
                    && soundPath.equals("block.portal.travel");
            if (isMasterFightMusic || isMasterArchitectSound || isMasterArchitectUi
                    || isMindWitnessCue || isMindTransitionCue) {
                return;
            }
            if (original.getSource() == SoundSource.MUSIC) {
                event.setSound(null);
                return;
            }
            event.setSound(new MuffledSound(
                    original,
                    MasterArchitectFloodClient.audioDuckFactor(),
                    0.94F));
            return;
        }

        if (CognitiveLoadClientState.shouldMuffleWorldSound(original)) {
            float factor = CognitiveLoadClientState.worldSoundVolumeFactor();
            if (factor <= 0.01F) {
                event.setSound(null);
            } else {
                event.setSound(new MuffledSound(original, factor, 0.94F));
            }
            return;
        }

        boolean isMasterAuraSound = soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)
                && (soundPath.startsWith("entity.master_architect.")
                || soundPath.startsWith("ui.master_architect."));
        float auraSilence = MasterArchitectAuraClient.silenceFactor();
        boolean isAuraAmbientSource = original.getSource() == SoundSource.AMBIENT
                || original.getSource() == SoundSource.WEATHER
                || original.getSource() == SoundSource.HOSTILE
                || original.getSource() == SoundSource.NEUTRAL;
        if (!isMasterAuraSound
                && original.getSource() != SoundSource.MASTER
                && original.getSource() != SoundSource.MUSIC
                && isAuraAmbientSource
                && auraSilence > 0.01F) {
            if (auraSilence >= 0.96F) {
                event.setSound(null);
            } else {
                event.setSound(new MuffledSound(
                        original,
                        1.0F - auraSilence,
                        1.0F - auraSilence * 0.12F));
            }
            return;
        }

        // All Frozen Dawn mob sounds are immune from ALL sound suppression,
        // including late-phase vacuum muting.
        if (isFrozenDawnEntitySound(soundLocation, soundPath)) {
            if (ThaevenTransmissionOverlay.isActive()) {
                event.setSound(new MuffledSound(original, 0.35F, 0.96F));
            }
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (PhaseManager.isVacuumActive(phase, progress) && !ApocalypseClientData.isBreathable()) {
            if (original.getSource() == SoundSource.MUSIC) return;
            if (original.getSource() == SoundSource.MASTER) return;
            if (soundPath.startsWith("ambient.eva_")) return;
            if (isLocalHearthrotHurtCue) return;
            if (isWindAmbience && (PostMaeveClientState.isMaeveErased()
                    || MasterArchitectWeather.getStrength() > 0.01F)) return;
            if (isGeothermalCue) {
                // Let geothermal vibration cues survive as near-silent suit/structure transmission
                // so vanilla subtitles can still track them in vacuum.
                event.setSound(new MuffledSound(original, 0.08f, 0.90f));
                return;
            }
            event.setSound(null);
            return;
        }

        if (ThaevenTransmissionOverlay.isActive()
                && original.getSource() != SoundSource.MASTER) {
            event.setSound(new MuffledSound(original, 0.35F, 0.96F));
            return;
        }

        SoundSource source = original.getSource();

        // Hollow proximity: distance-based sound suppression within 6 blocks
        // Hollow's own sounds always pass through; everything else fades with distance
        boolean isHollowSound = soundLocation.getNamespace().equals(FrozenDawn.MOD_ID)
                && soundPath.startsWith("entity.hollow.");

        if (!isHollowSound && source != SoundSource.MASTER && source != SoundSource.MUSIC) {
            AABB hollowScanBox = mc.player.getBoundingBox().inflate(6.0);
            List<HollowEntity> nearbyHollows = mc.player.level().getEntitiesOfClass(
                    HollowEntity.class, hollowScanBox);
            if (!nearbyHollows.isEmpty()) {
                // Find closest Hollow
                double closestDist = Double.MAX_VALUE;
                for (HollowEntity hollow : nearbyHollows) {
                    double d = mc.player.distanceTo(hollow);
                    if (d < closestDist) closestDist = d;
                }
                // Fade: full volume at 6 blocks, silence at 0 blocks
                float muffleIntensity = 1.0f - (float) Math.min(closestDist / 6.0, 1.0);
                if (muffleIntensity > 0.95f) {
                    event.setSound(null);
                    return;
                }
                if (muffleIntensity > 0.01f) {
                    float vol = 1.0f - muffleIntensity;
                    event.setSound(new MuffledSound(original, vol, 1.0f - muffleIntensity * 0.15f));
                    return;
                }
            }
        }

        // Cold-based muffling is a survival mechanic — skip for creative/spectator
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        // Normal cold-based muffling below -15C
        float temp = TemperatureHud.getDisplayedTemp();
        if (temp >= -15f) return;

        // Don't muffle music, UI, or our wind ambience
        if (original.getSource() == SoundSource.MUSIC) return;
        if (original.getSource() == SoundSource.MASTER) return;
        String path = original.getLocation().getPath();
        if (isWindAmbienceCue(original.getLocation(), path)) return;

        // Muffle intensity: 0 at -15C, full at -45C
        float intensity = Math.min(1f, (-temp - 15f) / 30f);

        float volumeMult = 1f - (intensity * 0.8f);   // down to 20% volume
        float pitchMult = 1f - (intensity * 0.25f);    // noticeable pitch drop

        event.setSound(new MuffledSound(original, volumeMult, pitchMult));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickBlockBreakSoundLimiter();
    }

    private static boolean shouldSuppressBlockBreakBurst(SoundInstance sound, ResourceLocation location) {
        if (sound.getSource() != SoundSource.BLOCKS) {
            return false;
        }
        if (!"minecraft".equals(location.getNamespace())) {
            return false;
        }
        String path = location.getPath();
        if (!path.startsWith("block.") || !path.endsWith(".break")) {
            return false;
        }
        if (blockBreakSoundsThisWindow < BLOCK_BREAK_SOUND_LIMIT_PER_WINDOW) {
            blockBreakSoundsThisWindow++;
            return false;
        }
        return true;
    }

    private static void tickBlockBreakSoundLimiter() {
        blockBreakSoundTicks++;
        if (blockBreakSoundTicks < BLOCK_BREAK_SOUND_WINDOW_TICKS) {
            return;
        }

        blockBreakSoundTicks = 0;
        blockBreakSoundsThisWindow = 0;
    }

    /**
     * Wraps a SoundInstance with modified volume and pitch.
     */
    private static class MuffledSound implements SoundInstance {
        private final SoundInstance wrapped;
        private final float volumeMult;
        private final float pitchMult;

        MuffledSound(SoundInstance wrapped, float volumeMult, float pitchMult) {
            this.wrapped = wrapped;
            this.volumeMult = volumeMult;
            this.pitchMult = pitchMult;
        }

        @Override public ResourceLocation getLocation() { return wrapped.getLocation(); }
        @Override public @Nullable WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) { return wrapped.resolve(manager); }
        @Override public net.minecraft.client.resources.sounds.Sound getSound() { return wrapped.getSound(); }
        @Override public SoundSource getSource() { return wrapped.getSource(); }
        @Override public boolean isLooping() { return wrapped.isLooping(); }
        @Override public boolean isRelative() { return wrapped.isRelative(); }
        @Override public int getDelay() { return wrapped.getDelay(); }
        @Override public float getVolume() { return wrapped.getVolume() * volumeMult; }
        @Override public float getPitch() { return wrapped.getPitch() * pitchMult; }
        @Override public double getX() { return wrapped.getX(); }
        @Override public double getY() { return wrapped.getY(); }
        @Override public double getZ() { return wrapped.getZ(); }
        @Override public Attenuation getAttenuation() { return wrapped.getAttenuation(); }
    }

    private static boolean isFrozenDawnEntitySound(ResourceLocation location, String path) {
        return location.getNamespace().equals(FrozenDawn.MOD_ID)
                && path.startsWith("entity.");
    }

    private static boolean isGeothermalCue(ResourceLocation location, String path) {
        return location.getNamespace().equals(FrozenDawn.MOD_ID)
                && path.startsWith("ambient.geothermal_");
    }

    private static boolean isSurveyorLensCue(ResourceLocation location, String path) {
        return location.getNamespace().equals(FrozenDawn.MOD_ID)
                && path.equals("item.surveyor_lens.tick");
    }

    private static boolean isBloomSporeCue(ResourceLocation location, String path) {
        return location.getNamespace().equals(FrozenDawn.MOD_ID)
                && path.startsWith("entity.bloom_spore.");
    }

    private static boolean isLocalHearthrotHurtCue(
            Minecraft minecraft,
            SoundInstance sound,
            ResourceLocation location,
            String path) {
        if (!location.getNamespace().equals(FrozenDawn.MOD_ID)
                || !path.startsWith("player.hearthrot.hurt_crack_")) {
            return false;
        }
        double dx = minecraft.player.getX() - sound.getX();
        double dy = minecraft.player.getY() - sound.getY();
        double dz = minecraft.player.getZ() - sound.getZ();
        return dx * dx + dy * dy + dz * dz <= 1.0D;
    }

    private static boolean shouldReplaceLocalPlayerHurt(
            Minecraft minecraft,
            SoundInstance sound,
            ResourceLocation location,
            String path) {
        if (HearthrotClientState.stage() < 3
                || sound.getSource() != SoundSource.PLAYERS
                || !location.getNamespace().equals("minecraft")
                || !isVanillaPlayerHurtPath(path)) {
            return false;
        }
        double dx = minecraft.player.getX() - sound.getX();
        double dy = minecraft.player.getY() - sound.getY();
        double dz = minecraft.player.getZ() - sound.getZ();
        return dx * dx + dy * dy + dz * dz <= 1.0D;
    }

    private static boolean isVanillaPlayerHurtPath(String path) {
        return path.equals("entity.player.hurt")
                || path.equals("entity.player.hurt_drown")
                || path.equals("entity.player.hurt_freeze")
                || path.equals("entity.player.hurt_on_fire")
                || path.equals("entity.player.hurt_sweet_berry_bush");
    }

    private static net.minecraft.sounds.SoundEvent randomHearthrotHurtSound(
            Minecraft minecraft) {
        return switch (minecraft.player.getRandom().nextInt(3)) {
            case 1 -> ModSounds.HEARTHROT_HURT_CRACK_TWO.get();
            case 2 -> ModSounds.HEARTHROT_HURT_CRACK_THREE.get();
            default -> ModSounds.HEARTHROT_HURT_CRACK_ONE.get();
        };
    }

    private static boolean isWindAmbienceCue(ResourceLocation location, String path) {
        return location.getNamespace().equals(FrozenDawn.MOD_ID)
                && (path.equals("ambient.wind_light")
                        || path.equals("ambient.wind_strong")
                        || path.startsWith("ambient/wind"));
    }
}
