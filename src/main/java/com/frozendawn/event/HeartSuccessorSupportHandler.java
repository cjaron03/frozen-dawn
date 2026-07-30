package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HeartScavengerWaveManager;
import com.frozendawn.homo.HeartSuccessorPolicy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Applies the defensive half of an active Successor support tether. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HeartSuccessorSupportHandler {
    private HeartSuccessorSupportHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getAmount() > 0.0F
                && HeartScavengerWaveManager.isSuccessorSupported(event.getEntity())) {
            event.setAmount(HeartSuccessorPolicy.mitigateSupportedDamage(
                    event.getAmount()));
        }
    }
}
