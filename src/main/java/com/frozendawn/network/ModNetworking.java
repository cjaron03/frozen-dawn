package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.ApocalypseClientData;
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
    }
}
