package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.maeve.MaeveDirector;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Executes a region order using loaded walking geometry and the pawn's own senses. */
final class ArchitectPawnController {
    private final ArchitectEntity actor;
    private DStarLitePathfinder path;
    private BlockPos goal;
    private Vec3 waypoint, progress;
    private long progressAt, nextSense;
    private UUID dispatch;
    private boolean fighting;
    ArchitectPawnController(ArchitectEntity actor) { this.actor = actor; }

    boolean tick() {
        var order = MaeveDirector.pawnOrder(actor);
        if (order == null) {
            if (actor.getPersistentData().hasUUID("macsConvergence")) {
                actor.getPersistentData().remove("macsConvergence"); clear();
                // Reload cancels the old group. Keep the permanent provenance marker.
                actor.beginMaeveDisengagement(actor.getUUID(), actor.blockPosition(), "PAWN_RELOAD_RELEASED");
                return true;
            }
            return false;
        }
        long now = actor.level().getServer().overworld().getGameTime();
        if (!order.dispatch().equals(dispatch)) { clear(); dispatch = order.dispatch(); progressAt = now; progress = actor.position(); }
        if (now < order.notBefore()) {
            actor.setCommitmentAction(false); actor.getNavigation().stop();
            actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
            return true;
        }
        if (fighting) return false;
        if (now >= nextSense) {
            nextSense = now + 10;
            // No target pointer, inventory or hidden player position informs the group order.
            var nearby = new java.util.ArrayList<ServerPlayer>();
            ((net.minecraft.server.level.ServerLevel) actor.level()).getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ServerPlayer.class),
                    actor.getBoundingBox().inflate(24), p -> p.isAlive() && !p.isCreative() && !p.isSpectator(), nearby, 8);
            var visible = nearby.stream().filter(actor::hasLineOfSight).toList();
            if (!visible.isEmpty()) {
                var player = visible.stream().min(java.util.Comparator.comparingDouble(actor::distanceToSqr)).orElseThrow();
                fighting = true; actor.setTarget(player); actor.resumeAfterReconnaissance();
                actor.recordDecision("MAEVE_PAWN_LOCAL_COMBAT", null, "dispatch=" + dispatch + " locallyVisible=" + player.getUUID());
                return false;
            }
        }
        actor.setCommitmentAction(false); actor.getNavigation().stop();
        if (progress == null || actor.position().distanceToSqr(progress) >= .25) { progress = actor.position(); progressAt = now; }
        if (actor.blockPosition().distSqr(order.destination()) < 4) {
            actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0); progressAt = now; return true;
        }
        if (now - Math.max(progressAt, order.notBefore()) >= 100) {
            actor.recordDecision("MAEVE_PAWN_ROUTE_FAILED", null, "no local walking progress");
            MaeveDirector.pawnRouteUnavailable(actor); return true;
        }
        if (waypoint != null && actor.position().subtract(waypoint).horizontalDistanceSqr() > .04) { move(waypoint); return true; }
        waypoint = null;
        if (path == null || actor.blockPosition().equals(goal)) {
            goal = segment(order.destination());
            if (goal == null) { MaeveDirector.pawnRouteUnavailable(actor); return true; }
            path = new DStarLitePathfinder(); path.configureObservedWalk(List.of());
            path.initialize(goal, actor.blockPosition(), actor.level());
        }
        path.updateStart(actor.blockPosition());
        if (!path.computePartial(80, actor.level())) return true;
        var step = path.getNextStep(actor.blockPosition(), actor.level());
        if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) { MaeveDirector.pawnRouteUnavailable(actor); return true; }
        waypoint = ArchitectWalkGeometry.observedStandingPosition(actor.level(), step.pos());
        if (waypoint == null) { MaeveDirector.pawnRouteUnavailable(actor); return true; }
        move(waypoint); return true;
    }
    private BlockPos segment(BlockPos destination) {
        Vec3 delta = Vec3.atBottomCenterOf(destination).subtract(actor.position());
        double length = delta.horizontalDistance(), scale = Math.min(1, 12 / Math.max(1, length));
        var center = BlockPos.containing(actor.getX() + delta.x * scale, actor.getY(), actor.getZ() + delta.z * scale);
        for (int radius = 0; radius <= 2; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                var candidate = center.offset(x, dy, z);
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), candidate) != null) return candidate;
            }
        }
        return null;
    }
    private void move(Vec3 next) {
        Vec3 delta = next.subtract(actor.position()); double length = delta.horizontalDistance();
        if (length < .01) return;
        double speed = Math.min(.16, length);
        actor.setDeltaMovement(delta.x / length * speed, actor.getDeltaMovement().y, delta.z / length * speed);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(next.x, next.y + 1, next.z, 15, 15);
    }
    void clear() { path = null; goal = null; waypoint = null; progress = null; dispatch = null; fighting = false; nextSense = 0; }
}
