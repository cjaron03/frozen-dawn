package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.client.DifficultySelectionScreen;
import com.frozendawn.client.SanityClientData;
import com.frozendawn.client.TemperatureHud;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Locale;

/**
 * Registers custom network payloads on the MOD bus.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(FrozenDawn.MOD_ID);
        registrar.playToClient(
                ApocalypseDataPayload.TYPE,
                ApocalypseDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ApocalypseClientData.update(payload))
        );
        registrar.playToClient(
                TemperaturePayload.TYPE,
                TemperaturePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> TemperatureHud.setTemperature(payload.temperature()))
        );
        registrar.playToClient(
                SanityStagePayload.TYPE,
                SanityStagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SanityClientData.setStage(payload.stage()))
        );
        registrar.playToClient(
                OpenDifficultySelectionPayload.TYPE,
                OpenDifficultySelectionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().setScreen(new DifficultySelectionScreen()))
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

                    if (preset == ConfigPresets.BRUTAL) {
                        Component message = Component.translatable("message.frozendawn.difficulty.brutal_good_luck")
                                .withStyle(ChatFormatting.DARK_RED);
                        for (ServerPlayer online : sp.getServer().getPlayerList().getPlayers()) {
                            online.sendSystemMessage(message);
                        }
                    }
                })
        );
    }
}
