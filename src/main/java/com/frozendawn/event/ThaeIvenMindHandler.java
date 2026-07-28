package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectMindSessionBridge;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.world.ThaeIvenMindDimension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Keeps Thae Iven a protected encounter stage rather than a mineable level. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ThaeIvenMindHandler {
    private ThaeIvenMindHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && ThaeIvenMindDimension.isMindLevel(player.level())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level
                && ThaeIvenMindDimension.isMindLevel(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (ThaeIvenMindDimension.isMindLevel(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ThaeIvenMindDimension.isMindLevel(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && ThaeIvenMindDimension.isMindLevel(event.getLevel())
                && event.getEntity() instanceof Mob
                && (!(event.getEntity() instanceof ArchitectEntity architect)
                        || !architect.isMasterMindCopy())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ThaeIvenMindDimension.isInsideStoredSanctuary(player)
                && event.getSource().is(ModDamageTypes.THAE_IVEN)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            player.getServer().execute(
                    () -> MasterArchitectMindSessionBridge.recoverStrandedPlayer(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            player.getServer().execute(
                    () -> MasterArchitectMindSessionBridge.recoverStrandedPlayer(player));
        }
    }
}
