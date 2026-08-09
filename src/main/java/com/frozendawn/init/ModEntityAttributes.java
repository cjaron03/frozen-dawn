package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.HeartSuccessorEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.entity.BloomSporeCorpseEntity;
import com.frozendawn.entity.ArchivistEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FROSTBITTEN.get(), FrostbittenEntity.createAttributes().build());
        event.put(ModEntities.FROSTMITE.get(), FrostmiteEntity.createAttributes().build());
        event.put(ModEntities.HOLLOW.get(), HollowEntity.createAttributes().build());
        event.put(ModEntities.RETURNED.get(), ReturnedEntity.createAttributes().build());
        event.put(ModEntities.UNDONE.get(), UndoneEntity.createAttributes().build());
        event.put(ModEntities.BLOOMBOUND_UNDONE.get(),
                UndoneEntity.createBloomboundAttributes().build());
        event.put(ModEntities.UNDONE_ARCHITECT.get(),
                UndoneArchitectEntity.createAttributes().build());
        event.put(ModEntities.BLOOM_SPORE.get(),
                BloomSporeEntity.createAttributes().build());
        event.put(ModEntities.BLOOM_SPORE_CORPSE.get(),
                BloomSporeCorpseEntity.createAttributes().build());
        event.put(ModEntities.ARCHIVIST.get(), ArchivistEntity.createAttributes().build());
        event.put(ModEntities.MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(ModEntities.ARCHITECT.get(), ArchitectEntity.createAttributes().build());
        event.put(ModEntities.HEART_SUCCESSOR.get(), HeartSuccessorEntity.createAttributes().build());
    }
}
