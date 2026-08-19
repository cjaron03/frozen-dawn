package com.frozendawn.network;

import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.client.DifficultySelectionScreen;
import com.frozendawn.client.ContinuityFractureInput;
import com.frozendawn.client.CognitiveLoadClientState;
import com.frozendawn.client.FrozenDawnEndingScreen;
import com.frozendawn.client.HearthSurveyAudio;
import com.frozendawn.client.HearthBoundaryEffects;
import com.frozendawn.client.HeartEchoClient;
import com.frozendawn.client.HeartMemoryNodeClient;
import com.frozendawn.client.MasterArchitectWeather;
import com.frozendawn.client.MasterArchitectAuraClient;
import com.frozendawn.client.MasterArchitectFightMusic;
import com.frozendawn.client.HeartBattleMusic;
import com.frozendawn.client.MasterArchitectFourthWallMoment;
import com.frozendawn.client.MasterArchitectFloodClient;
import com.frozendawn.client.MasterArchitectSeverTelegraph;
import com.frozendawn.client.MonitoringTerminalScreen;
import com.frozendawn.client.OrsaAwakeningIntro;
import com.frozendawn.client.PostMaeveClientState;
import com.frozendawn.client.StillpointClientState;
import com.frozendawn.client.BloomClientState;
import com.frozendawn.client.HearthrotClientState;
import com.frozendawn.client.RocketLaunchClientController;
import com.frozendawn.client.SanityClientData;
import com.frozendawn.client.ThermalVentClientEffects;
import com.frozendawn.client.SuitIntegrityClient;
import com.frozendawn.client.TemperatureHud;
import com.frozendawn.client.ThaevenTransmissionOverlay;
import com.frozendawn.client.TowerTerminalScreen;
import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Client-side packet handlers. This class is only loaded on the client
 * because it is never directly referenced by server code — only called
 * through lambdas in {@link ModNetworking} that execute client-side.
 */
public final class ClientHandlers {

    private ClientHandlers() {}

    public static void handleApocalypseData(ApocalypseDataPayload payload) {
        ApocalypseClientData.update(payload);
    }

    public static void handleTemperature(TemperaturePayload payload) {
        TemperatureHud.setTemperature(payload.temperature());
    }

    public static void handleBreathableState(BreathableStatePayload payload) {
        ApocalypseClientData.setBreathable(payload.breathable());
    }

    public static void handleSuitIntegrity(SuitIntegrityPayload payload) {
        SuitIntegrityClient.update(payload);
    }

    public static void handleSanityStage(SanityStagePayload payload) {
        SanityClientData.setStage(payload.stage());
    }

    public static void handleOpenDifficultySelection() {
        Minecraft.getInstance().setScreen(new DifficultySelectionScreen());
    }

    public static void handleOpenOrsaAwakening() {
        OrsaAwakeningIntro.start();
    }

    public static void handleOpenTowerTerminal(OpenTowerTerminalPayload payload) {
        TowerTerminalScreen.openOrUpdate(Minecraft.getInstance(), payload);
    }

    public static void handleOpenMonitoringTerminal(OpenMonitoringTerminalPayload payload) {
        MonitoringTerminalScreen.openOrUpdate(Minecraft.getInstance(), payload);
    }

    public static void handleOpenMeteorologistJournal(OpenMeteorologistJournalPayload payload) {
        BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(payload.stack());
        if (access != null) {
            Minecraft.getInstance().setScreen(new BookViewScreen(access));
        }
    }

    public static void handleThermalVentEruption(ThermalVentEruptionPayload payload) {
        ThermalVentClientEffects.triggerEruption(payload.pos(), payload.strength(), payload.durationTicks(), payload.radius());
    }

    public static void handleGeothermalCue(GeothermalCuePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ResourceLocation soundId = ResourceLocation.tryParse(payload.soundId());
        if (soundId == null) {
            return;
        }

        var sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId);
        if (sound.isEmpty()) {
            return;
        }

        mc.level.playLocalSound(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                sound.get(),
                SoundSource.AMBIENT,
                payload.volume(),
                payload.pitch(),
                false
        );
    }

    public static void handleLaunchSequence(LaunchSequencePayload payload) {
        RocketLaunchClientController.begin(payload);
    }

    public static void handleEndingSequence(EndingSequencePayload payload) {
        RocketLaunchClientController.resetForEnding();
        Minecraft.getInstance().setScreen(new FrozenDawnEndingScreen(payload));
    }

    public static void handleOpenThaevenTransmission(OpenThaevenTransmissionPayload payload) {
        ThaevenTransmissionOverlay.start(payload);
    }

    public static void handleCancelThaevenTransmission(CancelThaevenTransmissionPayload payload) {
        ThaevenTransmissionOverlay.cancelFromServer(payload.sessionId());
    }

    public static void handleHearthSurveyAudio(HearthSurveyAudioPayload payload) {
        HearthSurveyAudio.update(payload);
    }

    public static void handleMasterArchitectWeather(
            MasterArchitectWeatherPayload payload) {
        MasterArchitectWeather.update(payload);
    }

    public static void handleMasterArchitectAuraEvent(
            MasterArchitectAuraEventPayload payload) {
        MasterArchitectAuraClient.handleEvent(payload);
    }

    public static void handleMasterArchitectFightMusic(
            MasterArchitectFightMusicPayload payload) {
        MasterArchitectFightMusic.update(payload);
    }

    public static void handleHeartMusicState(HeartMusicStatePayload payload) {
        HeartBattleMusic.update(payload);
    }

    public static void handleMasterArchitectTetherHit(
            MasterArchitectTetherHitPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getEntity(payload.entityId()) instanceof ArchitectEntity architect) {
            architect.setClientMasterTetherFeedback(payload.feedbackStateId());
        }
    }

    public static void handleMasterArchitectSeverTelegraph(
            MasterArchitectSeverTelegraphPayload payload) {
        MasterArchitectSeverTelegraph.start(payload);
    }

    public static void handleMasterArchitectThermalSeverWarning(
            MasterArchitectThermalSeverWarningPayload payload) {
        MasterArchitectFloodClient.showWarningSuitDialogue(
                "ui.frozendawn.master_architect.thermal_sever_warning");
    }

    public static void handleContinuityFracture(
            ContinuityFracturePayload payload) {
        ContinuityFractureInput.start(payload);
    }

    public static void handleHearthBoundaryEffect(
            HearthBoundaryEffectPayload payload) {
        HearthBoundaryEffects.trigger(payload);
    }

    public static void handleMasterArchitectFourthWallState(
            MasterArchitectFourthWallStatePayload payload) {
        MasterArchitectFourthWallMoment.handleState(payload);
    }

    public static void handleMasterArchitectFloodState(
            MasterArchitectFloodStatePayload payload) {
        MasterArchitectFloodClient.handleState(payload);
    }

    public static void handleMasterArchitectFloodMote(
            MasterArchitectFloodMotePayload payload) {
        MasterArchitectFloodClient.handleMote(payload);
    }

    public static void handleMasterArchitectFloodProgress(
            MasterArchitectFloodProgressPayload payload) {
        MasterArchitectFloodClient.handleProgress(payload);
    }

    public static void handleCognitiveLoad(CognitiveLoadPayload payload) {
        CognitiveLoadClientState.update(payload);
    }

    public static void handlePostMaeveWorldState(
            PostMaeveWorldStatePayload payload) {
        PostMaeveClientState.update(payload);
    }

    public static void handleStillpointField(StillpointFieldPayload payload) {
        StillpointClientState.update(payload);
    }

    public static void handleBloomState(BloomStatePayload payload) {
        BloomClientState.update(payload);
    }

    public static void handleHearthrot(HearthrotPayload payload) {
        HearthrotClientState.update(payload);
    }

    public static void handleHearthrotSalvation() {
        HearthrotClientState.showSalvation();
    }

    public static void handleHeartEchoState(HeartEchoStatePayload payload) {
        HeartEchoClient.handleState(payload);
    }

    public static void handleHeartMemoryNodeEvent(
            HeartMemoryNodeEventPayload payload) {
        HeartMemoryNodeClient.handleEvent(payload);
    }
}
