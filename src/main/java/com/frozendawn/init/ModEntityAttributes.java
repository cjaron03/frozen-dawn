package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FROSTBITTEN.get(), FrostbittenEntity.createAttributes().build());
        event.put(ModEntities.HOLLOW.get(), HollowEntity.createAttributes().build());
        event.put(ModEntities.RETURNED.get(), ReturnedEntity.createAttributes().build());
        event.put(ModEntities.MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(ModEntities.ARCHITECT.get(), ArchitectEntity.createAttributes().build());
    }
}
