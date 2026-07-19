package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.MasterArchitectMusicStage;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectFightMusicPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Owns the layered, vacuum-safe score for the Master Architect fight. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectFightMusic {
    private static final int HEARTBEAT_TIMEOUT_TICKS = 70;
    private static final int GHOST_DURATION_TICKS = 744;
    private static final int LAST_WALL_DURATION_TICKS = 842;

    private static MasterArchitectMusicStage stage = MasterArchitectMusicStage.OFF;
    private static FightTrack tetherBed;
    private static FightTrack memoryFragment;
    private static int heartbeatTicks;

    private MasterArchitectFightMusic() {
    }

    public static void update(MasterArchitectFightMusicPayload payload) {
        MasterArchitectMusicStage incoming = MasterArchitectMusicStage.fromId(
                payload.stageId());
        if (incoming == MasterArchitectMusicStage.OFF) {
            hardStop();
            return;
        }

        heartbeatTicks = HEARTBEAT_TIMEOUT_TICKS;
        if (incoming == stage) {
            ensureTetherBed();
            return;
        }

        stage = incoming;
        ensureTetherBed();
        switch (incoming) {
            case KIT -> {
                tetherBed.setTargetVolume(0.48F);
                replaceMemoryFragment(
                        ModSounds.MASTER_ARCHITECT_MUSIC_GHOST.get(),
                        0.78F,
                        GHOST_DURATION_TICKS);
            }
            case TETHER -> {
                tetherBed.setTargetVolume(0.96F);
                stopMemoryFragment();
            }
            case LAST_WALL -> {
                tetherBed.setTargetVolume(0.56F);
                replaceMemoryFragment(
                        ModSounds.MASTER_ARCHITECT_MUSIC_LAST_WALL.get(),
                        0.88F,
                        LAST_WALL_DURATION_TICKS);
            }
            case OFF -> hardStop();
        }
    }

    public static boolean isActive() {
        return stage != MasterArchitectMusicStage.OFF && heartbeatTicks > 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            hardStop();
            return;
        }
        if (stage == MasterArchitectMusicStage.OFF) {
            return;
        }
        if (--heartbeatTicks <= 0) {
            hardStop();
            return;
        }
        ensureTetherBed();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardStop();
    }

    private static void ensureTetherBed() {
        if (tetherBed != null && !tetherBed.isStopped()) {
            return;
        }
        tetherBed = new FightTrack(
                ModSounds.MASTER_ARCHITECT_MUSIC_TETHERS.get(),
                0.0F,
                stage == MasterArchitectMusicStage.TETHER ? 0.96F
                        : stage == MasterArchitectMusicStage.LAST_WALL ? 0.56F : 0.48F,
                true,
                -1);
        Minecraft.getInstance().getSoundManager().play(tetherBed);
    }

    private static void replaceMemoryFragment(
            SoundEvent sound, float volume, int durationTicks) {
        stopMemoryFragment();
        memoryFragment = new FightTrack(sound, 0.0F, volume, false, durationTicks);
        Minecraft.getInstance().getSoundManager().play(memoryFragment);
    }

    private static void stopMemoryFragment() {
        if (memoryFragment != null) {
            Minecraft.getInstance().getSoundManager().stop(memoryFragment);
            memoryFragment = null;
        }
    }

    private static void hardStop() {
        Minecraft mc = Minecraft.getInstance();
        if (tetherBed != null) {
            mc.getSoundManager().stop(tetherBed);
            tetherBed = null;
        }
        stopMemoryFragment();
        stage = MasterArchitectMusicStage.OFF;
        heartbeatTicks = 0;
    }

    private static final class FightTrack extends AbstractTickableSoundInstance {
        private static final float FADE_STEP = 0.025F;

        private float targetVolume;
        private final int durationTicks;
        private int elapsedTicks;

        private FightTrack(
                SoundEvent sound,
                float initialVolume,
                float targetVolume,
                boolean looping,
                int durationTicks) {
            super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.volume = initialVolume;
            this.targetVolume = targetVolume;
            this.pitch = 1.0F;
            this.looping = looping;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.durationTicks = durationTicks;
        }

        private void setTargetVolume(float targetVolume) {
            this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = Mth.clamp(
                    Mth.approach(volume, targetVolume, FADE_STEP), 0.0F, 1.0F);
            elapsedTicks++;
            if (durationTicks > 0 && elapsedTicks >= durationTicks) {
                stop();
            }
        }
    }
}
