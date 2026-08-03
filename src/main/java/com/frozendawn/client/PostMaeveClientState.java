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

    private PostMaeveClientState() {
    }

    public static void update(PostMaeveWorldStatePayload payload) {
        boolean newlyErased = !maeveErased && payload.maeveErased();
        maeveErased = payload.maeveErased();
        undoneSpawningReleased = payload.undoneSpawningReleased();
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

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        maeveErased = false;
        undoneSpawningReleased = false;
    }
}
