package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Local bounded D* walking and an observable inspection pause on real disproof. */
final class ArchitectSpatialCommitment {
    private final ArchitectEntity actor;
    private DStarLitePathfinder path;
    private ArchitectAccessInspection inspection;
    private BlockPos goal;
    private long nextDiscovery, nextReplan;

    ArchitectSpatialCommitment(ArchitectEntity actor) { this.actor = actor; }

    void tick(MaeveDirector.PositionDirective directive, LivingEntity target, long now) {
        if (now >= nextDiscovery && directive.obstruction() == null) {
            nextDiscovery = now + 10;
            if (MaeveDirector.discoverAccess(actor, directive)) {
                directive = MaeveDirector.positionDirective(actor);
                actor.recordDecision("MAEVE_ACCESS_DISCOVERY", null, "obstruction=" + directive.obstruction());
            }
        }
        if (directive.obstruction() != null) {
            actor.setMaeveHolding(true); actor.setCommitmentAction(true);
            face(directive.obstruction().getCenter());
            return;
        }
        Vec3 destination = Vec3.atBottomCenterOf(directive.position());
        if (actor.position().distanceToSqr(destination) <= .36) {
            MaeveDirector.commitmentArrived(actor);
            actor.setMaeveHolding(true); actor.setCommitmentAction(true);
            face(directive.spatial().inside().getCenter().add(0, 1, 0));
            if (target != null && actor.distanceToSqr(target) < 2.8 * 2.8 && actor.hasLineOfSight(target) && actor.attackAnim == 0) {
                actor.swing(InteractionHand.MAIN_HAND); actor.doHurtTarget(target);
            }
            return;
        }
        actor.setMaeveHolding(false); actor.setCommitmentAction(false);
        if (inspection != null) {
            if (!inspection.compute()) return;
            BlockPos step = inspection.next();
            if (step != null) { move(Vec3.atBottomCenterOf(step)); return; }
            if (MaeveDirector.discoverAccess(actor, directive)) {
                actor.recordDecision("MAEVE_ACCESS_DISCOVERY", null, "inspection vantage");
                actor.setMaeveHolding(true); actor.setCommitmentAction(true);
            } else { MaeveDirector.releaseCommitment(actor, "NO_RECOVERABLE_ACCESS_ROUTE"); clear(); }
            return;
        }
        if (path == null || !directive.position().equals(goal) || actor.horizontalCollision && now >= nextReplan) {
            goal = directive.position(); path = new DStarLitePathfinder();
            path.configureObservedWalk(MaeveDirector.knownDangers(actor, directive.player()));
            path.initialize(goal, actor.blockPosition(), actor.level()); nextReplan = now + 20;
        }
        if (actor.blockPosition().equals(goal)) { move(destination); return; }
        path.updateStart(actor.blockPosition());
        if (!path.computePartial(80, actor.level())) return;
        var step = path.getNextStep(actor.blockPosition(), actor.level());
        if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) {
            inspection = new ArchitectAccessInspection(actor, directive);
            actor.recordDecision("MAEVE_ACCESS_INSPECTION_ROUTE", null, "remembered point unreachable");
            return;
        }
        Vec3 next = step.pos().equals(actor.blockPosition()) ? destination : Vec3.atBottomCenterOf(step.pos());
        move(next);
    }

    private void move(Vec3 next) {
        Vec3 delta = next.subtract(actor.position()); double length = delta.horizontalDistance();
        if (length > .01) {
            double speed = Math.min(.16, length);
            actor.setDeltaMovement(delta.x / length * speed, actor.getDeltaMovement().y, delta.z / length * speed);
            face(next.add(0, 1, 0));
        }
    }

    private void face(Vec3 point) {
        float yaw = (float) (Math.atan2(point.z - actor.getZ(), point.x - actor.getX()) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(point.x, point.y, point.z, 15, 15);
    }

    void clear() { path = null; inspection = null; goal = null; nextDiscovery = 0; nextReplan = 0; actor.setMaeveHolding(false); }
}
