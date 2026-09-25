package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Walk to a remembered crossing, inspect, then extract. Never pursues a hidden player. */
final class ArchitectReconnaissanceController {
    private final ArchitectEntity actor;
    private DStarLitePathfinder path;
    private BlockPos goal;
    private long nextPlan, inspectingAt = -1, lastProgress;
    private Vec3 progress;
    private String report = "UNSEEN";

    ArchitectReconnaissanceController(ArchitectEntity actor) { this.actor = actor; }

    boolean tick(ServerPlayer candidate) {
        long now = actor.getServer().overworld().getGameTime();
        var packet = MaeveDirector.missionPacket(actor);
        if (packet == null && candidate != null && now >= nextPlan && actor.onGround()
                && !actor.isInWaterOrBubble() && !actor.isOnFire()) {
            nextPlan = now + 20;
            if (MaeveDirector.requestReconnaissance(actor, candidate)) {
                packet = MaeveDirector.missionPacket(actor);
                actor.cancelMaeveAttentionWork();
            }
        }
        if (packet == null) return false;
        actor.setReconnaissanceEyes(true);
        if (!actor.isAlive() || actor.isMasterArchitectVisual() || actor.isInWaterOrBubble() || actor.isOnFire()
                || actor.getHealth() < actor.getMaxHealth() * .6 || now >= packet.expiresAt()
                || !packet.dimension().equals(actor.level().dimension().location().toString())) {
            MaeveDirector.finishMission(actor, "LOCAL_SAFETY_RELEASE", true); return true;
        }
        actor.getNavigation().stop(); actor.setTarget(null); actor.setSprinting(false);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        if (goal == null) {
            Vec3 out = Vec3.atBottomCenterOf(packet.access().outside());
            Vec3 direction = out.subtract(Vec3.atBottomCenterOf(packet.access().inside())).multiply(1, 0, 1).normalize();
            goal = inspectionGround(BlockPos.containing(out.add(direction.scale(3))));
            if (goal == null) {
                MaeveDirector.finishMission(actor, "LOCAL_ROUTE_UNAVAILABLE", true); return true;
            }
            actor.recordDecision("MAEVE_RECON_ROUTE", null,
                    "mission=" + packet.id() + " observed=" + packet.access().outside() + " stance=" + goal);
            path = new DStarLitePathfinder(); path.configureObservedWalk(packet.dangers());
            path.initialize(goal, walkingStart(), actor.level());
            progress = actor.position(); lastProgress = now;
        }
        Vec3 destination = ArchitectWalkGeometry.observedStandingPosition(actor.level(), goal);
        if (destination == null) {
            MaeveDirector.finishMission(actor, "LOCAL_ROUTE_UNAVAILABLE", true); return true;
        }
        if (actor.position().distanceToSqr(destination) <= .36) {
            actor.setCommitmentAction(true); actor.setMaeveHolding(true);
            face(packet.access().inside().getCenter().add(0, 1, 0));
            if (inspectingAt < 0) {
                inspectingAt = now;
                actor.recordDecision("MAEVE_RECON_INSPECTING", null, "mission=" + packet.id() + " access=" + packet.access().outside());
            }
            if ((now - inspectingAt) % 10 == 0) report = MaeveDirector.inspectMission(actor);
            if (now - inspectingAt >= 100) MaeveDirector.finishMission(actor,
                    report.equals("UNSEEN") ? "SURVEY_INCONCLUSIVE" : "SURVEY_COMPLETE", true);
            return true;
        }
        actor.setMaeveHolding(false); actor.setCommitmentAction(false);
        if (!actor.onGround()) return true;
        if (actor.position().distanceToSqr(progress) > .25) { progress = actor.position(); lastProgress = now; }
        if (now - lastProgress >= 80 || Math.abs(actor.getY() - destination.y) > 1) {
            MaeveDirector.finishMission(actor, "LOCAL_ROUTE_UNAVAILABLE", true); return true;
        }
        BlockPos start = walkingStart();
        if (!start.equals(goal)) {
            path.updateStart(start);
            if (!path.computePartial(80, actor.level())) return true;
            var step = path.getNextStep(start, actor.level());
            if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) {
                MaeveDirector.finishMission(actor, "LOCAL_ROUTE_UNAVAILABLE", true); return true;
            }
            destination = ArchitectWalkGeometry.observedStandingPosition(actor.level(), step.pos());
            if (destination == null) {
                MaeveDirector.finishMission(actor, "LOCAL_ROUTE_UNAVAILABLE", true); return true;
            }
        }
        Vec3 delta = destination.subtract(actor.position()); double length = delta.horizontalDistance();
        if (length > .01) {
            double speed = Math.min(.16, length);
            actor.setDeltaMovement(delta.x / length * speed, actor.getDeltaMovement().y, delta.z / length * speed);
            face(destination.add(0, 1, 0));
        }
        return true;
    }

    /** Collision can raise the feet before the center leaves the lower partial surface. */
    private BlockPos walkingStart() {
        BlockPos feet = actor.blockPosition();
        if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet) != null) return feet;
        Vec3 lower = ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet.below());
        return lower != null && Math.abs(actor.getY() - lower.y) <= .6 ? feet.below() : feet;
    }

    /** A crossing can be witnessed mid-jump. Ground only the local stance, not the saved evidence. */
    private BlockPos inspectionGround(BlockPos projected) {
        for (int dy : new int[] {0, -1, 1, -2, 2}) {
            BlockPos candidate = projected.offset(0, dy, 0);
            Vec3 standing = ArchitectWalkGeometry.observedStandingPosition(actor.level(), candidate);
            if (standing != null && Math.abs(actor.getY() - standing.y) <= 1) return candidate;
        }
        return null;
    }

    private void face(Vec3 point) {
        float yaw = (float) (Math.atan2(point.z - actor.getZ(), point.x - actor.getX()) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(point.x, point.y, point.z, 15, 15);
    }

    void clear() {
        if (path != null) path.cleanup(); path = null; goal = null; inspectingAt = -1; progress = null;
        nextPlan = 0; report = "UNSEEN";
        actor.setMaeveHolding(false); actor.getNavigation().stop();
        actor.setReconnaissanceEyes(false);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
    }
}
