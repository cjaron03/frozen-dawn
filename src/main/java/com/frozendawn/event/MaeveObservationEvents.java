package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class MaeveObservationEvents {
    private MaeveObservationEvents() { }

    @SubscribeEvent
    public static void onPresence(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ArchitectEntity observer && !observer.level().isClientSide()
                && observer.level().getGameTime() % 10 == 0) MaeveDirector.observePresence(observer);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ArchitectEntity observer) {
            MaeveDirector.observeDamage(observer, event.getSource(), event.getNewDamage());
            observer.onEffectiveCombatDamage(event.getSource(), event.getNewDamage());
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof ArchitectEntity actor) {
            MaeveDirector.observeCounterDamage(actor, player, event.getNewDamage(), true);
            if (event.getNewDamage() > 0) MaeveDirector.beginLocalCombat(actor, player);
        }
    }

    @SubscribeEvent
    public static void onRecovery(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MaeveDirector.observeRecovery(player, event.getItem());
        }
    }
}
