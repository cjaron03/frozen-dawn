package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HeartMusicStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Owns the music channel from Heart formation until its future canonical death. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HeartBattleMusic {
    private static HeartTrack track;
    private static boolean active;

    private HeartBattleMusic() {
    }

    public static void update(HeartMusicStatePayload payload) {
        if (!payload.active()) {
            hardStop();
            return;
        }
        if (!active) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getMusicManager().stopPlaying();
            minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
            MasterArchitectFightMusic.stopAll();
        }
        active = true;
        ensureTrack();
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            hardStop();
            return;
        }
        if (!active) {
            return;
        }
        minecraft.getMusicManager().stopPlaying();
        ensureTrack();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardStop();
    }

    private static void ensureTrack() {
        if (!active || track != null && !track.isStopped()) {
            return;
        }
        track = new HeartTrack();
        Minecraft.getInstance().getSoundManager().play(track);
    }

    private static void hardStop() {
        if (track != null) {
            Minecraft.getInstance().getSoundManager().stop(track);
            track = null;
        }
        active = false;
    }

    private static final class HeartTrack extends AbstractTickableSoundInstance {
        private HeartTrack() {
            super(ModSounds.THAE_IVEN_HEART_MUSIC.get(), SoundSource.MUSIC,
                    SoundInstance.createUnseededRandom());
            relative = true;
            looping = true;
            delay = 0;
            attenuation = Attenuation.NONE;
            volume = 0.92F;
            pitch = 1.0F;
        }

        @Override
        public void tick() {
            if (!HeartBattleMusic.active) {
                stop();
            }
        }
    }
}
