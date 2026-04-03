package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SoundInstance original = event.getSound();
        if (original == null) return;

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (PhaseManager.isVacuumActive(phase, progress) && !ApocalypseClientData.isBreathable()) {
            if (original.getSource() == SoundSource.MUSIC) return;
            if (original.getSource() == SoundSource.MASTER) return;
            if (original.getLocation().getPath().startsWith("ambient.eva_")) return;
            event.setSound(null);
            return;
        }

        // All Frozen Dawn mob sounds are immune from ALL sound suppression
        SoundSource source = original.getSource();
        String soundPath = original.getLocation().getPath();
        if (original.getLocation().getNamespace().equals(FrozenDawn.MOD_ID)
                && soundPath.startsWith("entity.")) {
            return; // Always plays at full volume
        }

        // Hollow proximity: distance-based sound suppression within 6 blocks
        // Hollow's own sounds always pass through; everything else fades with distance
        boolean isHollowSound = original.getLocation().getNamespace().equals(FrozenDawn.MOD_ID)
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
        if (path.startsWith("ambient/wind")) return;

        // Muffle intensity: 0 at -15C, full at -45C
        float intensity = Math.min(1f, (-temp - 15f) / 30f);

        float volumeMult = 1f - (intensity * 0.8f);   // down to 20% volume
        float pitchMult = 1f - (intensity * 0.25f);    // noticeable pitch drop

        event.setSound(new MuffledSound(original, volumeMult, pitchMult));
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
}
