package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.PostMaeveWorldStatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client mirror of the irreversible world-scoped Maeve state. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PostMaeveClientState {
    private static boolean maeveErased;
    private static boolean undoneSpawningReleased;
    private static long moonriseStartDayTime = -1L;
    private static long moonElapsedDayTicks = -1L;
    private static long moonSyncDayTime = -1L;
    private static long moonVisualSeed;
    private static boolean moonriseStarted;
    private static long sessionMaximumMoonElapsed = -1L;

    private PostMaeveClientState() {
    }

    public static void update(PostMaeveWorldStatePayload payload) {
        boolean newlyErased = !maeveErased && payload.maeveErased();
        boolean moonTimelineChanged = moonriseStartDayTime
                != payload.moonriseStartDayTime()
                || moonVisualSeed != payload.moonVisualSeed()
                || moonriseStarted != payload.moonriseStarted()
                || (moonElapsedDayTicks >= 0L
                && payload.moonElapsedDayTicks() < moonElapsedDayTicks);
        maeveErased = payload.maeveErased();
        undoneSpawningReleased = payload.undoneSpawningReleased();
        moonriseStartDayTime = payload.moonriseStartDayTime();
        moonElapsedDayTicks = payload.moonElapsedDayTicks();
        moonSyncDayTime = payload.moonSyncDayTime();
        moonVisualSeed = payload.moonVisualSeed();
        moonriseStarted = payload.moonriseStarted();
        sessionMaximumMoonElapsed = moonTimelineChanged
                ? moonElapsedDayTicks
                : Math.max(sessionMaximumMoonElapsed, moonElapsedDayTicks);
        if (newlyErased) {
            CognitiveLoadClientState.reset();
            MasterArchitectWeather.reset();
            MasterArchitectAuraClient.clear();
            MasterArchitectSkyFaceRenderer.clear();
            ThaevenTransmissionOverlay.clearForPostMaeve();
            HearthSurveyAudio.reset();
        }
    }

    public static boolean isMaeveErased() {
        return maeveErased;
    }

    public static boolean isUndoneSpawningReleased() {
        return undoneSpawningReleased;
    }

    public static long moonElapsedDayTicks(long currentDayTime) {
        if (!maeveErased || !moonriseStarted || moonElapsedDayTicks < 0L) {
            return -1L;
        }
        long localAdvance = currentDayTime > moonSyncDayTime
                ? currentDayTime - moonSyncDayTime : 0L;
        sessionMaximumMoonElapsed = Math.max(
                sessionMaximumMoonElapsed, moonElapsedDayTicks + localAdvance);
        return sessionMaximumMoonElapsed;
    }

    public static long moonriseStartDayTime() {
        return moonriseStartDayTime;
    }

    public static long moonVisualSeed() {
        return moonVisualSeed;
    }

    public static boolean isMoonriseStarted() {
        return moonriseStarted;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        maeveErased = false;
        undoneSpawningReleased = false;
        moonriseStartDayTime = -1L;
        moonElapsedDayTicks = -1L;
        moonSyncDayTime = -1L;
        moonVisualSeed = 0L;
        moonriseStarted = false;
        sessionMaximumMoonElapsed = -1L;
    }
}
