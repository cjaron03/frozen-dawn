package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Prevents vanilla strike side effects inside a living Master aura. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class MasterArchitectAuraEvents {
    private MasterArchitectAuraEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt
                && event.getLevel() instanceof ServerLevel level
                && HearthMasterArchitectWeatherManager.suppressesVanillaLightning(
                        level, event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }
}
