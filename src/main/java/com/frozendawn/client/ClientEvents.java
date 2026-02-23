package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client-side MOD bus event handlers.
 * Registers GUI layers (frost overlay).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frost_overlay"),
                FrostOverlay::render
        );
    }
}
