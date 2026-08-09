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
import com.frozendawn.homo.CognitiveLoadManager;
import com.frozendawn.homo.HeartEchoManager;
import com.frozendawn.homo.HeartMemoryNodeManager;
import com.frozendawn.homo.HeartMaeveErasureManager;
import com.frozendawn.homo.MasterArchitectFourthWallManager;
import com.frozendawn.entity.UndoneEntity;
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
                SuitIntegrityPayload.TYPE,
                SuitIntegrityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleSuitIntegrity(payload))
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
                MasterArchitectAuraEventPayload.TYPE,
                MasterArchitectAuraEventPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectAuraEvent(payload))
        );
        registrar.playToClient(
                MasterArchitectFightMusicPayload.TYPE,
                MasterArchitectFightMusicPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectFightMusic(payload))
        );
        registrar.playToClient(
                HeartMusicStatePayload.TYPE,
                HeartMusicStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHeartMusicState(payload))
        );
        registrar.playToClient(
                MasterArchitectTetherHitPayload.TYPE,
                MasterArchitectTetherHitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectTetherHit(payload))
        );
        registrar.playToClient(
                MasterArchitectSeverTelegraphPayload.TYPE,
                MasterArchitectSeverTelegraphPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectSeverTelegraph(payload))
        );
        registrar.playToClient(
                MasterArchitectThermalSeverWarningPayload.TYPE,
                MasterArchitectThermalSeverWarningPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectThermalSeverWarning(payload))
        );
        registrar.playToClient(
                ContinuityFracturePayload.TYPE,
                ContinuityFracturePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleContinuityFracture(payload))
        );
        registrar.playToClient(
                HearthBoundaryEffectPayload.TYPE,
                HearthBoundaryEffectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHearthBoundaryEffect(payload))
        );
        registrar.playToClient(
                MasterArchitectFourthWallStatePayload.TYPE,
                MasterArchitectFourthWallStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectFourthWallState(payload))
        );
        registrar.playToClient(
                MasterArchitectFloodStatePayload.TYPE,
                MasterArchitectFloodStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectFloodState(payload))
        );
        registrar.playToClient(
                MasterArchitectFloodMotePayload.TYPE,
                MasterArchitectFloodMotePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectFloodMote(payload))
        );
        registrar.playToClient(
                MasterArchitectFloodProgressPayload.TYPE,
                MasterArchitectFloodProgressPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleMasterArchitectFloodProgress(payload))
        );
        registrar.playToClient(
                CognitiveLoadPayload.TYPE,
                CognitiveLoadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleCognitiveLoad(payload))
        );
        registrar.playToClient(
                PostMaeveWorldStatePayload.TYPE,
                PostMaeveWorldStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handlePostMaeveWorldState(payload))
        );
        registrar.playToClient(
                BloomStatePayload.TYPE,
                BloomStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleBloomState(payload))
        );
        registrar.playToClient(
                HearthrotPayload.TYPE,
                HearthrotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHearthrot(payload))
        );
        registrar.playToClient(
                HearthrotSalvationPayload.TYPE,
                HearthrotSalvationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        ClientHandlers::handleHearthrotSalvation)
        );
        registrar.playToClient(
                HeartEchoStatePayload.TYPE,
                HeartEchoStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHeartEchoState(payload))
        );
        registrar.playToClient(
                HeartMemoryNodeEventPayload.TYPE,
                HeartMemoryNodeEventPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHandlers.handleHeartMemoryNodeEvent(payload))
        );

        // Server-bound packets
        registrar.playToServer(
                CognitiveResistancePayload.TYPE,
                CognitiveResistancePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        CognitiveLoadManager.handleResistance(sp, payload.resistance());
                    }
                })
        );
        registrar.playToServer(
                UndoneStrugglePayload.TYPE,
                UndoneStrugglePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)
                            || !Float.isFinite(payload.input())) {
                        return;
                    }
                    player.serverLevel().getEntitiesOfClass(
                            UndoneEntity.class,
                            player.getBoundingBox().inflate(16.0D),
                            undone -> undone.getGraspTargetId() == player.getId())
                            .stream().findFirst()
                            .ifPresent(undone -> undone.applyStruggle(
                                    player, payload.input()));
                })
        );
        registrar.playToServer(
                HeartEchoActionPayload.TYPE,
                HeartEchoActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        HeartEchoManager.handleAction(sp, payload);
                    }
                })
        );
        registrar.playToServer(
                HeartMemoryNodeStrikePayload.TYPE,
                HeartMemoryNodeStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        HeartMemoryNodeManager.handleStrike(
                                sp, payload.nodeIndex(), payload.renderedLoad());
                    }
                })
        );
        registrar.playToServer(
                HeartMaeveErasePayload.TYPE,
                HeartMaeveErasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        HeartMaeveErasureManager.handlePulse(sp);
                    }
                })
        );
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
                MasterArchitectFourthWallRequestPayload.TYPE,
                MasterArchitectFourthWallRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        MasterArchitectFourthWallManager.handleRequest(sp, payload);
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
