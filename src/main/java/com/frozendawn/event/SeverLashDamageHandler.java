package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModDamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/** The Lash is a cutter: armor and shields stop half of what they would stop. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class SeverLashDamageHandler {
    private SeverLashDamageHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().is(ModDamageTypes.SEVER_LASH))
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction / 2);
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getDamageSource().is(ModDamageTypes.SEVER_LASH))
            event.setBlockedDamage(event.getOriginalBlockedDamage() / 2);
    }
}
