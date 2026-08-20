package com.frozendawn.aggregate;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModParticles;
import net.minecraft.server.level.ServerLevel;
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
            emitPressureSignal(player.serverLevel(), event.getEntity());
            AggregateGrowthManager.leaveResidue(
                    player.serverLevel(), event.getEntity().blockPosition(), data);
        }
    }

    public static void markIgnored(Entity entity) {
        entity.getPersistentData().putBoolean(IGNORE_PRESSURE_TAG, true);
    }

    private static void emitPressureSignal(ServerLevel level, Entity source) {
        for (int strand = 0; strand < 12; strand++) {
            double angle = strand * 2.399963229728653D;
            double radius = 0.08D + (strand % 4) * 0.045D;
            double x = source.getX() + Math.cos(angle) * radius;
            double z = source.getZ() + Math.sin(angle) * radius;
            double rise = 0.58D + strand * 0.014D;
            level.sendParticles(ModParticles.AGGREGATE_PRESSURE_SIGNAL.get(),
                    x, source.getY() + source.getBbHeight() + 0.25D
                            + (strand % 3) * 0.10D, z,
                    0, Math.cos(angle) * 0.012D, rise,
                    Math.sin(angle) * 0.012D, 1.0D);
        }
    }
}
