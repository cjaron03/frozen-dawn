package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostbittenEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FROSTBITTEN.get(), FrostbittenEntity.createAttributes().build());
    }
}
