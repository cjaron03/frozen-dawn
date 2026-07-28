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

/** Owns the continuous, vacuum-safe score for the Master Architect fight. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectFightMusic {
    private static final int HEARTBEAT_TIMEOUT_TICKS = 70;

    private static MasterArchitectMusicStage stage = MasterArchitectMusicStage.OFF;
    private static FightTrack battleScore;
    private static int heartbeatTicks;
    private static int terminalSuppressionTicks;

    private MasterArchitectFightMusic() {
    }

    public static void update(MasterArchitectFightMusicPayload payload) {
        if (terminalSuppressionTicks > 0) {
            hardStop();
            return;
        }
        MasterArchitectMusicStage incoming = MasterArchitectMusicStage.fromId(
                payload.stageId());
        if (incoming == MasterArchitectMusicStage.OFF) {
            hardStop();
            return;
        }

        heartbeatTicks = HEARTBEAT_TIMEOUT_TICKS;
        if (incoming == stage) {
            if (incoming != MasterArchitectMusicStage.FLOOD) {
                ensureBattleScore();
            }
            return;
        }

        stage = incoming;
        if (incoming == MasterArchitectMusicStage.FLOOD) {
            stopBattleScore();
        } else {
            ensureBattleScore();
            battleScore.setTargetVolume(stageVolume(incoming));
        }
    }

    public static boolean isActive() {
        return stage != MasterArchitectMusicStage.OFF && heartbeatTicks > 0;
    }

    public static void stopFlood() {
        hardStop();
    }

    /** Hard encounter teardown used when the Master dies or the Flood closes. */
    public static void stopAll() {
        hardStop();
    }

    public static void setFloodIntensity(float strength, float proximity) {
        if (terminalSuppressionTicks > 0) {
            hardStop();
            return;
        }
        heartbeatTicks = HEARTBEAT_TIMEOUT_TICKS;
        if (stage != MasterArchitectMusicStage.FLOOD) {
            stage = MasterArchitectMusicStage.FLOOD;
        }
        stopBattleScore();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (terminalSuppressionTicks > 0) {
            terminalSuppressionTicks--;
            hardStop();
            return;
        }
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
        if (stage != MasterArchitectMusicStage.FLOOD) {
            ensureBattleScore();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardStop();
        terminalSuppressionTicks = 0;
    }

    public static void suppressAfterCanonicalDeath(int ticks) {
        terminalSuppressionTicks = Math.max(terminalSuppressionTicks, ticks);
        hardStop();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
        minecraft.getMusicManager().stopPlaying();
    }

    private static void ensureBattleScore() {
        if (battleScore != null && !battleScore.isStopped()) {
            return;
        }
        battleScore = new FightTrack(
                ModSounds.MASTER_ARCHITECT_MUSIC_ORREN.get(),
                0.0F,
                stageVolume(stage),
                true,
                -1);
        Minecraft.getInstance().getSoundManager().play(battleScore);
    }

    private static float stageVolume(MasterArchitectMusicStage currentStage) {
        return switch (currentStage) {
            case KIT -> 0.78F;
            case TETHER -> 0.92F;
            case LAST_WALL -> 0.88F;
            case FLOOD, OFF -> 0.0F;
        };
    }

    private static void stopBattleScore() {
        if (battleScore != null) {
            Minecraft.getInstance().getSoundManager().stop(battleScore);
            battleScore = null;
        }
    }

    private static void hardStop() {
        stopBattleScore();
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
