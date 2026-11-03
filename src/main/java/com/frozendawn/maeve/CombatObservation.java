package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Actual damage and confirmed shield blocks share the same local witness boundary. */
final class CombatObservation {
    private CombatObservation() { }
    static void record(MaeveSavedData data, MissionPlanner missions, LearningCoordinator learning,
                       ArchitectEntity observer, DamageSource source, float amount, boolean blocked) {
        if (data.store() == null || !(amount > 0) || !Float.isFinite(amount)) return;
        long now = observer.getServer().overworld().getGameTime();
        if (blocked) {
            var state = data.store().commitmentFor(observer.getUUID(), now);
            if (!(source.getEntity() instanceof ServerPlayer player)
                    || !ObservationCollector.canObserve(observer, player, false)) {
                if (state != null) state.performance().finish("UNOBSERVED_DAMAGE");
                data.setDirty(); return;
            }
            ObservationCollector.sword(data.store(), missions, observer, player, source, now, true);
            if (state != null) {
                if (!state.active(now).player().equals(player.getUUID())) state.performance().finish("UNOBSERVED_DAMAGE");
                else state.performance().block(observer.getUUID(), player.getUUID(),
                        observer.level().dimension().location().toString(), observer.blockPosition(), now, amount);
            }
        } else {
            learning.incoming(observer, source, amount);
            ObservationCollector.damage(data.store(), missions, observer, source, amount, now);
            SpatialObservations.damage(data.store(), observer, source, amount, now);
        }
        data.setDirty();
    }
}
