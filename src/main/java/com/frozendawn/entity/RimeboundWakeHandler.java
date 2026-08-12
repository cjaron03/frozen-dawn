package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Routes nearby block interaction into dormant wake checks. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RimeboundWakeHandler {
    private RimeboundWakeHandler() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            notify(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            notify(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            notify(level, event.getPos());
        }
    }

    private static void notify(ServerLevel level, BlockPos pos) {
        for (RimeboundEntity rimebound : level.getEntitiesOfClass(
                RimeboundEntity.class, new AABB(pos).inflate(10.0D))) {
            rimebound.notifyTerrainInteraction(pos);
        }
    }
}
