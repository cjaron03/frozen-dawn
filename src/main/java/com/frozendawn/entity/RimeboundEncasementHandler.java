package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-side interaction lock while Rimebound ice fully encloses a player. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RimeboundEncasementHandler {
    private RimeboundEncasementHandler() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        if (isSolid(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isSolid(Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && RimeboundEncasement.isFrozenSolid(serverPlayer)) {
            return true;
        }
        var effect = player.getEffect(ModEffects.RIMEBOUND_ENCASEMENT);
        return effect != null && effect.getAmplifier() >= 3;
    }
}
