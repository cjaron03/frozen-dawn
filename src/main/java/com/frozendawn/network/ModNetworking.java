package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.client.SanityClientData;
import com.frozendawn.client.TemperatureHud;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
        registrar.playToServer(
                WatcherSeenPayload.TYPE,
                WatcherSeenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        WorldTickHandler.grantAdvancement(sp, "watcher_seen");
                    }
                })
        );
    }
}
