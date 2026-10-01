package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.PacifistAdvancement;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class PacifistKillHandler {
    private PacifistKillHandler() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled()) PacifistAdvancement.recordDeath(event.getEntity(), event.getSource());
    }
}
