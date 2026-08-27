package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.mixin.ChannelAccessor;
import com.frozendawn.mixin.SoundEngineAccessor;
import com.frozendawn.mixin.SoundManagerAccessor;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Original OpenAL EFX low-pass for sounds crossing inward through the field. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StillpointAudioFilter {
    private static final int UPDATE_INTERVAL = 4;
    private static int tickCounter;
    private static int filter;
    private static boolean checkedSupport;
    private static boolean supported;
    private static float transition;
    private static boolean listenerWasInside;
    private static final Set<SoundInstance> pocketSounds = Collections.newSetFromMap(
            new IdentityHashMap<>());

    private StillpointAudioFilter() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        boolean soundPhysics = ModList.get().isLoaded("sound_physics_remastered");

        boolean listenerInside = FrozenDawnClientConfig.ENABLE_STILLPOINT_AUDIO_MUFFLING.get()
                && StillpointClientState.isListenerInside();
        transition = approach(transition, listenerInside ? 1.0F : 0.0F, 0.5F);
        SoundEngine engine = ((SoundManagerAccessor) minecraft.getSoundManager())
                .frozendawn$getSoundEngine();
        Map<SoundInstance, ChannelAccess.ChannelHandle> sounds =
                ((SoundEngineAccessor) engine).frozendawn$getInstanceToChannel();
        if (listenerWasInside && !listenerInside) {
            stopPocketSounds(sounds);
        }
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : sounds.entrySet()) {
            SoundInstance sound = entry.getKey();
            boolean pocketSound = listenerInside && isPocketSound(sound);
            if (pocketSound) pocketSounds.add(sound);
            if (!soundPhysics) {
                boolean apply = transition > 0.01F && isPocketSound(sound);
                entry.getValue().execute(channel -> {
                    ensureSupport();
                    attach(channel, apply ? filter : 0);
                });
            }
        }
        pocketSounds.retainAll(sounds.keySet());
        listenerWasInside = listenerInside;
    }

    private static boolean isPocketSound(SoundInstance sound) {
        SoundSource source = sound.getSource();
        if (sound.isRelative() || source == SoundSource.MASTER
                || source == SoundSource.MUSIC || source == SoundSource.VOICE) {
            return false;
        }
        return !StillpointClientState.isOutsideSource(
                sound.getX(), sound.getY(), sound.getZ());
    }

    private static void stopPocketSounds(
            Map<SoundInstance, ChannelAccess.ChannelHandle> sounds) {
        for (SoundInstance sound : pocketSounds) {
            ChannelAccess.ChannelHandle handle = sounds.get(sound);
            if (handle != null) handle.execute(Channel::stop);
        }
        pocketSounds.clear();
    }

    public static boolean useFallback() {
        if (!FrozenDawnClientConfig.ENABLE_STILLPOINT_AUDIO_MUFFLING.get()) return false;
        if (ModList.get().isLoaded("sound_physics_remastered")) return true;
        return checkedSupport && !supported;
    }

    private static void ensureSupport() {
        if (checkedSupport) return;
        checkedSupport = true;
        try {
            long context = ALC10.alcGetCurrentContext();
            long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
            supported = device != 0L
                    && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
            if (supported) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE,
                        EXTEfx.AL_FILTER_LOWPASS);
                updateFilterParameters();
            }
        } catch (RuntimeException exception) {
            supported = false;
            filter = 0;
            FrozenDawn.LOGGER.warn("Stillpoint EFX unavailable; using volume fallback", exception);
        }
    }

    private static void updateFilterParameters() {
        if (!supported || filter == 0) return;
        float gain = 1.0F - transition * 0.64F;
        float highFrequency = 1.0F - transition * 0.92F;
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, highFrequency);
    }

    private static void attach(Channel channel, int filterId) {
        if (!supported) return;
        updateFilterParameters();
        int source = ((ChannelAccessor) channel).frozendawn$getSource();
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
    }

    private static float approach(float value, float target, float amount) {
        return value < target ? Math.min(target, value + amount)
                : Math.max(target, value - amount);
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        checkedSupport = false;
        supported = false;
        filter = 0;
        transition = 0.0F;
        listenerWasInside = false;
        pocketSounds.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        transition = 0.0F;
        listenerWasInside = false;
        pocketSounds.clear();
    }
}
