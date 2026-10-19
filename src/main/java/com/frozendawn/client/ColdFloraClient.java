package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.flora.ColdFlora;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the Rimewood sign and hanging sign textures with the sign atlas. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ColdFloraClient {
    private ColdFloraClient() { }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Sheets.addWoodType(ColdFlora.RIMEWOOD));
    }
}
