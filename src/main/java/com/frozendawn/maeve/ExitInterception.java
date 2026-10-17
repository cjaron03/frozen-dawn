package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Causal join of an arrived watch and an independently validated, continuous crossing. */
final class ExitInterception {
    private ExitInterception() { }
    record Watch(UUID area, BlockPos anchor, String from, boolean alternative) { }

    static Watch start(MaeveDirector.PositionDirective directive, WorldModel world) {
        if (directive == null || directive.spatial() == null || world == null || directive.arrivedAt() < 0) return null;
        var area = world.area(directive.evidence().dimension());
        if (area == null) return null;
        var pair = ExitPrediction.parse(directive.pattern());
        if (pair != null && !pair.area().equals(ExitPrediction.token(area.id()))) return null;
        String from = pair == null ? WorldModel.bearing(area.anchor(), directive.position()) : pair.from();
        return from == null ? null : new Watch(area.id(), area.anchor(), from, pair != null);
    }

    static void witnessed(BeliefStore store, MissionPlanner missions, ArchitectEntity observer,
                          ServerPlayer player, BlockPos outside, long now) {
        if (!CommitmentCoordinator.eligible(observer, player)) return;
        var policy = store.commitment(player.getUUID());
        var directive = policy == null ? null : policy.active(now);
        if (directive == null) return;
        // The witness can differ, but a dead, unloaded or reassigned waiting actor cannot cause an interception.
        if (!(player.serverLevel().getEntity(directive.observer()) instanceof ArchitectEntity waiting)
                || waiting.isMasterArchitectVisual() || waiting.isHearthAssessor() || waiting.isHearthPopulationResident()
                || com.frozendawn.aggregate.AggregateReinforcementManager.isChild(waiting) || !waiting.isAlive() || waiting.isNoAi()
                || waiting.getTarget() != player || waiting.blockPosition().distSqr(directive.position()) > 4) return;
        record(store, player.getUUID(), observer.getUUID(), player.level().dimension().location().toString(), outside, now,
                missions != null && missions.observing(observer, player, now) != null ? BeliefPolicy.RECON_SUPPORT : BeliefPolicy.SUPPORT);
    }

    static void record(BeliefStore store, UUID player, UUID observer, String dimension, BlockPos outside, long now, double support) {
        var policy = store.commitment(player);
        var directive = policy == null ? null : policy.active(now);
        var watch = policy == null ? null : policy.exitWatch();
        var world = store.world(player);
        var area = world == null ? null : world.area(dimension);
        if (directive == null || watch == null || directive.obstruction() != null || area == null
                || !watch.area().equals(area.id()) || !dimension.equals(directive.evidence().dimension())
                || outside.distSqr(watch.anchor()) > 32 * 32 || now <= directive.arrivedAt()) return;
        String actual = WorldModel.bearing(watch.anchor(), outside);
        if (actual == null) return;
        String provenance = "EXIT_WATCH from=" + watch.from() + " at=" + directive.position().toShortString()
                + " arrived=" + directive.arrivedAt() + " actor=" + directive.observer() + " outward=" + actual;
        if (watch.alternative()) {
            // Observing B while guarding B is not another failed interception at A.
            // Only disproof applies here; never manufacture a third-level hypothesis.
            if (!actual.equals(ExitPrediction.parse(directive.pattern()).to()))
                store.record(player, observer, dimension, outside, now, directive.pattern(), false, provenance + " WRONG_ALTERNATIVE");
            return;
        }
        for (var belief : store.snapshot(player, now)) {
            var pair = ExitPrediction.parse(belief.pattern());
            if (pair != null && pair.area().equals(ExitPrediction.token(area.id())) && pair.from().equals(watch.from())
                    && !pair.to().equals(actual))
                store.record(player, observer, dimension, outside, now, belief.pattern(), false, provenance + " DIFFERENT_RESPONSE");
        }
        if (!actual.equals(watch.from()))
            store.record(player, observer, dimension, outside, now, ExitPrediction.key(watch.from(), actual, area.id()),
                    true, provenance + " FAILED_PRIMARY_INTERCEPTION", support);
    }
}
