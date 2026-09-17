package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server boundary only. Position construction and movement belong to the local Architect. */
final class CommitmentCoordinator {
    private CommitmentCoordinator() { }

    static boolean eligible(ArchitectEntity observer, ServerPlayer player) {
        return !observer.isMasterArchitectVisual() && !observer.isHearthAssessor()
                && !observer.isHearthPopulationResident() && ObservationCollector.canObserve(observer, player, false);
    }

    static List<MaeveDirector.CommitmentHint> hints(MaeveSavedData data, ArchitectEntity observer, ServerPlayer player) {
        var store = data.store();
        if (store == null || !eligible(observer, player)) return List.of();
        long now = player.serverLevel().getServer().overworld().getGameTime();
        if (!store.contact(player.getUUID(), observer.getUUID(), player.level().dimension().location().toString(), now)) return List.of();
        data.setDirty();
        return store.commitment(player.getUUID()).hints(now).stream()
                .filter(h -> h.evidence().dimension().equals(observer.level().dimension().location().toString()))
                .filter(h -> observer.blockPosition().distSqr(h.evidence().position()) <= ObservationCollector.RANGE * ObservationCollector.RANGE)
                .toList();
    }

    static boolean choose(MaeveSavedData data, ArchitectEntity observer, ServerPlayer player,
                          List<MaeveDirector.PositionCandidate> candidates) {
        var hints = hints(data, observer, player);
        if (hints.isEmpty()) return false;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        var store = data.store();
        if (store.commitmentFor(observer.getUUID(), now) != null) return false;
        var local = candidates.stream().limit(6)
                .filter(c -> hints.stream().anyMatch(h -> h.pattern().equals(c.pattern())))
                .filter(c -> observer.blockPosition().distSqr(c.position()) <= (c.spatial() == null ? 36 : 24 * 24)
                        && observer.level().hasChunkAt(c.position())
                        && (c.cover() == null || (observer.blockPosition().distSqr(c.cover()) <= 9
                        && observer.level().hasChunkAt(c.cover()))))
                .filter(c -> WorldModel.BEARINGS.contains(c.pattern()) == (c.spatial() != null))
                .filter(c -> c.spatial() == null || SpatialObservations.validCandidate(store, observer, player, c, now)).toList();
        boolean selected = store.commitment(player.getUUID()).choose(player.getUUID(), observer.getUUID(), local, now);
        data.setDirty();
        return selected;
    }

    static MaeveDirector.PositionDirective directive(MaeveSavedData data, ArchitectEntity observer) {
        if (data.store() == null) return null;
        long now = observer.getServer().overworld().getGameTime();
        var policy = data.store().commitmentFor(observer.getUUID(), now);
        return policy == null ? null : policy.active(now);
    }

    static void arrived(MaeveSavedData data, ArchitectEntity observer) {
        if (data.store() == null) return;
        long now = observer.getServer().overworld().getGameTime();
        var policy = data.store().commitmentFor(observer.getUUID(), now);
        if (policy != null) { policy.arrived(now); data.setDirty(); }
    }

    static void release(MaeveSavedData data, ArchitectEntity observer, String reason) {
        if (data.store() == null) return;
        var policy = data.store().commitmentFor(observer.getUUID(), observer.getServer().overworld().getGameTime());
        if (policy != null) { policy.finish(reason); data.setDirty(); }
    }

    static void stopAll(MinecraftServer server, BeliefStore store) {
        if (store == null) return;
        for (var state : store.commitments()) {
            // Also stop an executor whose policy just expired but whose entity has
            // not ticked yet; erasure must clear pending movement immediately.
            var directive = state.selected();
            if (directive == null) continue;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(directive.evidence().dimension())));
            if (level != null && level.getEntity(directive.observer()) instanceof ArchitectEntity observer) {
                observer.clearMaevePositioning();
            }
            state.finish("ERASED");
        }
    }
}
