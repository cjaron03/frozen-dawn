package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.block.MonitoringStationTerminalBlockEntity;
import com.frozendawn.block.TowerAntennaConsoleBlockEntity;
import com.frozendawn.event.IceClawsHandler;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthTransmissionManager;
import com.frozendawn.world.RocketLaunchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Locale;

/**
 * Registers custom network payloads on the MOD bus.
 * Client-side handlers are isolated in {@link ClientHandlers} so the server
 * never tries to load client-only classes (Minecraft, Screen, etc.).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(FrozenDawn.MOD_ID);

        // Client-bound packets — handlers delegate to ClientHandlers (only loaded on client)
        registrar.playToClient(
                ApocalypseDataPayload.TYPE,
                ApocalypseDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleApocalypseData(payload))
        );
        registrar.playToClient(
                TemperaturePayload.TYPE,
                TemperaturePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleTemperature(payload))
        );
        registrar.playToClient(
                BreathableStatePayload.TYPE,
                BreathableStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleBreathableState(payload))
        );
        registrar.playToClient(
                SanityStagePayload.TYPE,
                SanityStagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleSanityStage(payload))
        );
        registrar.playToClient(
                OpenDifficultySelectionPayload.TYPE,
                OpenDifficultySelectionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientHandlers::handleOpenDifficultySelection)
        );
        registrar.playToClient(
                OpenOrsaAwakeningPayload.TYPE,
                OpenOrsaAwakeningPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientHandlers::handleOpenOrsaAwakening)
        );
        registrar.playToClient(
                OpenTowerTerminalPayload.TYPE,
                OpenTowerTerminalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleOpenTowerTerminal(payload))
        );
        registrar.playToClient(
                OpenMonitoringTerminalPayload.TYPE,
                OpenMonitoringTerminalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleOpenMonitoringTerminal(payload))
        );
        registrar.playToClient(
                OpenMeteorologistJournalPayload.TYPE,
                OpenMeteorologistJournalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleOpenMeteorologistJournal(payload))
        );
        registrar.playToClient(
                ThermalVentEruptionPayload.TYPE,
                ThermalVentEruptionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleThermalVentEruption(payload))
        );
        registrar.playToClient(
                GeothermalCuePayload.TYPE,
                GeothermalCuePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleGeothermalCue(payload))
        );
        registrar.playToClient(
                LaunchSequencePayload.TYPE,
                LaunchSequencePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleLaunchSequence(payload))
        );
        registrar.playToClient(
                EndingSequencePayload.TYPE,
                EndingSequencePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandlers.handleEndingSequence(payload))
        );
        registrar.playToClient(
                OpenThaevenTransmissionPayload.TYPE,
                OpenThaevenTransmissionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleOpenThaevenTransmission(payload))
        );
        registrar.playToClient(
                CancelThaevenTransmissionPayload.TYPE,
                CancelThaevenTransmissionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleCancelThaevenTransmission(payload))
        );
        registrar.playToClient(
                HearthSurveyAudioPayload.TYPE,
                HearthSurveyAudioPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHearthSurveyAudio(payload))
        );
        registrar.playToClient(
                MasterArchitectWeatherPayload.TYPE,
                MasterArchitectWeatherPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectWeather(payload))
        );
        registrar.playToClient(
                ContinuityFracturePayload.TYPE,
                ContinuityFracturePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleContinuityFracture(payload))
        );

        // Server-bound packets
        registrar.playToServer(
                WatcherSeenPayload.TYPE,
                WatcherSeenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        WorldTickHandler.grantAdvancement(sp, "watcher_seen");
                    }
                })
        );
        registrar.playToServer(
                IceClawsInputPayload.TYPE,
                IceClawsInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        BlockPos anchorPos = payload.hasAnchor() ? payload.anchorPos() : null;
                        var wallSide = payload.hasAnchor() ? IceClawsHandler.decodeWallSide(payload.wallSide2d()) : null;
                        IceClawsHandler.setClimbInput(sp, payload.jumpHeld(), anchorPos, wallSide);
                    }
                })
        );
        registrar.playToServer(
                SubmitTowerTerminalPayload.TYPE,
                SubmitTowerTerminalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) {
                        return;
                    }
                    if (sp.level().getBlockEntity(payload.pos()) instanceof TowerAntennaConsoleBlockEntity console) {
                        console.submitAction(sp, payload.nonce(), payload.actionType(), payload.actionIndex(), payload.typedGuess());
                    }
                })
        );
        registrar.playToServer(
                SubmitMonitoringTerminalPayload.TYPE,
                SubmitMonitoringTerminalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) {
                        return;
                    }
                    if (sp.level().getBlockEntity(payload.pos()) instanceof MonitoringStationTerminalBlockEntity terminal) {
                        terminal.submitAction(sp, payload.nonce(), payload.actionType(), payload.actionIndex(), payload.typedGuess());
                    }
                })
        );
        registrar.playToServer(
                SelectDifficultyPresetPayload.TYPE,
                SelectDifficultyPresetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp) || sp.getServer() == null) {
                        return;
                    }

                    ApocalypseState state = ApocalypseState.get(sp.getServer());
                    if (state.isDifficultyLocked()) {
                        sp.sendSystemMessage(Component.translatable("message.frozendawn.difficulty.locked")
                                .withStyle(ChatFormatting.GRAY));
                        return;
                    }

                    ConfigPresets preset;
                    try {
                        preset = ConfigPresets.valueOf(payload.presetName().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        return;
                    }

                    boolean applied = DifficultyPresetManager.applyPreset(sp.getServer(), preset, true, false);
                    if (!applied) {
                        return;
                    }

                    WorldTickHandler.trySendOrsaAwakening(sp);

                    if (preset == ConfigPresets.BRUTAL) {
                        Component message = Component.translatable("message.frozendawn.difficulty.brutal_good_luck")
                                .withStyle(ChatFormatting.DARK_RED);
                        for (ServerPlayer online : sp.getServer().getPlayerList().getPlayers()) {
                            online.sendSystemMessage(message);
                        }
                    }
                })
        );
        registrar.playToServer(
                RocketLaunchInputPayload.TYPE,
                RocketLaunchInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        RocketLaunchManager.handleLaunchJump(sp);
                    }
                })
        );
        registrar.playToServer(
                ThaevenTransmissionResultPayload.TYPE,
                ThaevenTransmissionResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        HearthTransmissionManager.handleResult(
                                sp, payload.sessionId(), payload.completed());
                    }
                })
        );
    }
}
