package com.frozendawn.maeve;

import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** All spatial reports originate in an eligible observer's local perception. */
final class SpatialObservations {
    private SpatialObservations() { }

    static void presence(BeliefStore store, MissionPlanner missions, ArchitectEntity observer, ServerPlayer player, long now) {
        if (!ObservationCollector.canObserve(observer, player, false)) {
            var world = store.world(player.getUUID()); if (world != null) world.forget(observer.getUUID());
            return;
        }
        String dim = player.level().dimension().location().toString();
        var world = store.observeContact(player.getUUID(), observer.getUUID(), dim, now);
        BlockPos pos = player.blockPosition(); boolean covered = !player.level().canSeeSky(pos);
        var previous = world.sample(observer.getUUID(), dim, pos, covered, now);
        world.seen(dim, observer.blockPosition(), now);
        if (previous != null && previous.dimension().equals(dim) && previous.covered() != covered
                && now > previous.time() && now - previous.time() <= 10
                && pos.distSqr(previous.position()) > 0 && pos.distSqr(previous.position()) <= 16
                && !player.level().canSeeSky(previous.position()) == previous.covered()
                && Math.abs(pos.getY() - previous.position().getY()) <= 1
                && visible(observer, previous.position().getCenter().add(0, .5, 0), null, 48)) {
            BlockPos inside = covered ? pos : previous.position(), outside = covered ? previous.position() : pos;
            var evidence = evidence(store, player, observer, outside, now, "WITNESSED_SKY_BOUNDARY_CROSSING");
            world.access(dim, outside, inside, evidence);
            String bearing = WorldModel.bearing(world.center(dim), outside);
            if (!covered && bearing != null) {
                ExitInterception.witnessed(store, missions, observer, player, outside, now);
                ObservationCollector.record(store, missions, observer, player, outside, now, bearing, true, "WITNESSED_OUTWARD_CROSSING");
                for (var belief : store.snapshot(player.getUUID(), now)) {
                    if (WorldModel.BEARINGS.contains(belief.pattern()) && !bearing.equals(belief.pattern())) {
                        ObservationCollector.record(store, missions, observer, player, outside, now, belief.pattern(), false, "WITNESSED_OTHER_RETREAT_BEARING");
                    }
                }
            }
        }
        if (now % 20 == 0) {
            for (BlockPos heater : HeaterRegistry.nearby(observer.level(), observer.blockPosition(), 24, 16)) {
                if (visible(observer, heater.getCenter(), heater, 24)) world.event("HEAT_SOURCE", dim, heater,
                        evidence(store, player, observer, heater, now, "WITNESSED_LIT_HEATER"));
            }
        }
    }

    static void damage(BeliefStore store, ArchitectEntity observer, DamageSource source, float damage, long now) {
        if (!(damage > 0) || !Float.isFinite(damage) || observer.isNoAi() || observer.isRemoved()
                || observer.isMasterArchitectVisual() || AggregateReinforcementManager.isChild(observer)) return;
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p
                : observer.getTarget() instanceof ServerPlayer p ? p : null;
        if (player == null || player.isCreative() || player.isSpectator() || player.level() != observer.level()) return;
        var world = store.world(player.getUUID());
        if (world == null || (!world.recent(observer.getUUID(), now) && !ObservationCollector.canObserve(observer, player, true))) return;
        if (observer.isAlive() && damage < observer.getMaxHealth() * .2F) return;
        world.event("DANGER_ZONE", observer.level().dimension().location().toString(), observer.blockPosition(),
                evidence(store, player, observer, observer.blockPosition(), now,
                        observer.isAlive() ? "ARCHITECT_SUFFERED_HEAVY_DAMAGE" : "ARCHITECT_FATAL_DAMAGE"));
    }

    static boolean validCandidate(BeliefStore store, ArchitectEntity observer, ServerPlayer player,
                                  MaeveDirector.PositionCandidate candidate, long now) {
        var world = store.world(player.getUUID());
        if (world == null || !ExitPrediction.spatial(candidate.pattern())) return false;
        var resolved = world.resolve(observer.level().dimension().location().toString(), candidate.pattern(), observer.blockPosition(), now);
        return resolved != null && resolved.equals(candidate.spatial()) && resolved.outside().equals(candidate.position());
    }

    static boolean discover(BeliefStore store, ArchitectEntity observer, MaeveDirector.PositionDirective directive, long now) {
        if (store == null || observer.isMasterArchitectVisual() || directive == null || directive.spatial() == null || directive.obstruction() != null
                || !directive.observer().equals(observer.getUUID()) || !observer.isAlive() || observer.isNoAi()) return false;
        var policy = store.commitmentFor(observer.getUUID(), now);
        var world = store.world(directive.player());
        if (policy == null || world == null || policy.selected() != directive
                || observer.blockPosition().distSqr(directive.position()) > 16) return false;
        Vec3 start = Vec3.atBottomCenterOf(directive.spatial().inside());
        Vec3 end = Vec3.atBottomCenterOf(directive.spatial().outside());
        int steps = Math.min(8, Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2)));
        for (int i = 0; i <= steps; i++) {
            BlockPos feet = BlockPos.containing(start.lerp(end, (double) i / steps));
            for (int y = 0; y < 2; y++) {
                BlockPos block = feet.above(y);
                if (!observer.level().hasChunkAt(block)) continue;
                var state = observer.level().getBlockState(block);
                if (state.getCollisionShape(observer.level(), block).isEmpty()
                        || !visible(observer, block.getCenter(), block, 8)) continue;
                var evidence = new ObservedEvidence(observer.getUUID(), directive.encounter(), directive.evidence().dimension(),
                        block, now, "OBSERVED_ACCESS_OBSTRUCTION", true);
                if (!world.obstructed(directive.evidence().dimension(), directive.position(), evidence)) return false;
                store.disprove(directive, observer.getUUID(), block, now);
                policy.discover(block, now);
                return true;
            }
        }
        return false;
    }

    private static ObservedEvidence evidence(BeliefStore store, ServerPlayer player, ArchitectEntity observer,
                                             BlockPos pos, long now, String action) {
        return new ObservedEvidence(observer.getUUID(), store.commitment(player.getUUID()).encounter(),
                observer.level().dimension().location().toString(), pos, now, action, true);
    }

    private static boolean visible(ArchitectEntity observer, Vec3 target, BlockPos surface, double range) {
        if (observer.getEyePosition().distanceToSqr(target) > range * range) return false;
        BlockPos end = BlockPos.containing(target), from = observer.blockPosition();
        for (int x = Math.min(from.getX(), end.getX()) >> 4; x <= Math.max(from.getX(), end.getX()) >> 4; x++) {
            for (int z = Math.min(from.getZ(), end.getZ()) >> 4; z <= Math.max(from.getZ(), end.getZ()) >> 4; z++) {
                if (!observer.level().hasChunk(x, z)) return false;
            }
        }
        var hit = observer.level().clip(new ClipContext(observer.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer));
        return hit.getType() == HitResult.Type.MISS || surface != null && hit.getBlockPos().equals(surface);
    }
}
