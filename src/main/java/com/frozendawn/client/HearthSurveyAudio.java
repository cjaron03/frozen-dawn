package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HearthSurveyPolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.network.HearthSurveyAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HearthSurveyAudio {
    private static final int SIGNAL_TIMEOUT_TICKS = 30;
    private static boolean active;
    private static float proximity;
    private static int signalTimeout;
    private static int ticksUntilClick;

    private HearthSurveyAudio() {
    }

    public static void update(HearthSurveyAudioPayload payload) {
        if (!payload.active()) {
            reset();
            return;
        }

        boolean newlyActive = !active;
        active = true;
        proximity = Mth.clamp(payload.proximity(), 0.0F, 1.0F);
        signalTimeout = SIGNAL_TIMEOUT_TICKS;
        if (newlyActive) {
            ticksUntilClick = 2;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        if (!active) {
            return;
        }
        if (--signalTimeout <= 0
                || SurveyorLensScanner.heldProfile(
                minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem()) == null) {
            reset();
            return;
        }
        if (ThaevenTransmissionOverlay.isActive()) {
            ticksUntilClick = 2;
            return;
        }
        if (ticksUntilClick-- > 0) {
            return;
        }

        minecraft.level.playLocalSound(
                minecraft.player.getX(),
                minecraft.player.getY(),
                minecraft.player.getZ(),
                ModSounds.SURVEYOR_LENS_TICK.get(),
                SoundSource.PLAYERS,
                0.68F,
                1.0F,
                false
        );

        ticksUntilClick = HearthSurveyPolicy.sampleGeigerIntervalTicks(
                proximity, minecraft.level.random.nextFloat());
    }

    public static void reset() {
        active = false;
        proximity = 0.0F;
        signalTimeout = 0;
        ticksUntilClick = 0;
    }
}
