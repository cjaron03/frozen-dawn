package com.frozendawn.aggregate;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Adds convergence pressure exactly once at the real player-attributed death hook. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class AggregatePressureHandler {
    public static final String IGNORE_PRESSURE_TAG = "frozendawn:aggregate_ignore_pressure";

    private AggregatePressureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !FrozenDawnConfig.ENABLE_AGGREGATE.get()
                || player.isCreative() || player.isSpectator()
                || player.getServer() == null
                || !PostMaeveWorldState.isErased(player.getServer())
                || StillpointPolicy.isSuppressed(player.serverLevel(),
                event.getEntity().blockPosition())
                || event.getEntity().getPersistentData().getBoolean(IGNORE_PRESSURE_TAG)) {
            return;
        }
        AggregateSavedData data = AggregateSavedData.get(player.getServer());
        if (data.resolved()) return;
        AggregatePressurePolicy.Contribution contribution =
                AggregatePressurePolicy.classify(event.getEntity());
        if (data.addPressure(contribution)
                && player.serverLevel() == event.getEntity().level()) {
            AggregateGrowthManager.leaveResidue(
                    player.serverLevel(), event.getEntity().blockPosition(), data);
        }
    }

    public static void markIgnored(Entity entity) {
        entity.getPersistentData().putBoolean(IGNORE_PRESSURE_TAG, true);
    }
}
