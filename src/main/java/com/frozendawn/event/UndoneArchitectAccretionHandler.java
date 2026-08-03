package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.UndoneArchitectEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Adds only eligible Frozen Dawn bodies to an Undone Architect's accretion. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class UndoneArchitectAccretionHandler {
    private static final TagKey<EntityType<?>> ACCRETION_MATERIALS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "undone_architect_accretion_materials"));

    private UndoneArchitectAccretionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity()
                instanceof UndoneArchitectEntity architect)
                || event.getEntity() instanceof Player
                || !event.getEntity().getType().is(ACCRETION_MATERIALS)) {
            return;
        }
        architect.gainAccretion(event.getEntity());
    }
}
