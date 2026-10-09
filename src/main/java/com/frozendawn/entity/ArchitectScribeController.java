package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.HeaterRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * §9.4b local executor. Walks out into the cold, stands at a distance and watches, flees when approached,
 * and fights only when cornered (§9.13a local-defense exception). It never initiates combat.
 * Reload restarts the local watch; the claim's lifetime still bounds the whole appearance.
 */
final class ArchitectScribeController {
    private enum Phase { TRAVEL, WATCH, FLEE, DEPART }
    private static final int[] HEADINGS = {0, 45, -45, 90, -90};
    private static final double WATCH_RADIUS = 20, WATCH_RANGE = 48, FLEE_RADIUS = 12, CALM_RADIUS = 20, GONE_RADIUS = 32;
    private static final double CORNERED_RANGE = 3.5, WALK_SPEED = .14, FLEE_SPEED = .2;
    private static final long WATCH = 2400, DEFEND = 200, DEPART_LIMIT = 600, STALL = 100, CALM = 40, FLEE_STALL = 30;
    private final ArchitectEntity actor;
    private Phase phase = Phase.TRAVEL;
    private BlockPos post;
    private Vec3 threat, waypoint, progress;
    private BlockPos goal;
    private DStarLitePathfinder path;
    private long progressAt, replanAt, watchedFrom = -1, departedAt = -1, calmSince = -1, defendUntil;
    private UUID attacker;
    private String departure, postReport = "none";

    ArchitectScribeController(ArchitectEntity actor) { this.actor = actor; }
    private long now() { return actor.getServer().overworld().getGameTime(); }

    boolean tick() {
        if (!actor.isScribe()) return false;
        long now = now();
        if (now < defendUntil) return false;
        if (attacker != null) {
            attacker = null; actor.setTarget(null);
            actor.recordDecision("MAEVE_SCRIBE_DEFENSE_ENDED", null, "no recent damage; resumes fleeing");
            setPhase(Phase.FLEE, now);
        }
        var order = MaeveDirector.scribeOrder(actor);
        if (phase != Phase.DEPART && (order == null || !order.dimension().equals(actor.level().dimension().location().toString())))
            depart(order == null ? "ORDER_ENDED" : "DIMENSION_CHANGED", now);
        actor.getNavigation().stop(); actor.setTarget(null); actor.setSprinting(false);
        actor.setMaeveHolding(false); actor.setCommitmentAction(false);
        var near = nearestPlayer(FLEE_RADIUS, true);
        if (near != null && phase != Phase.DEPART) {
            threat = near.position(); calmSince = -1;
            if (phase != Phase.FLEE) setPhase(Phase.FLEE, now);
        }
        switch (phase) {
            case FLEE -> {
                if (nearestPlayer(CALM_RADIUS, false) == null) {
                    if (calmSince < 0) calmSince = now;
                    if (now - calmSince >= CALM) { post = null; setPhase(Phase.TRAVEL, now); }
                } else calmSince = -1;
                walk(threat == null ? actor.getLookAngle().reverse() : actor.position().subtract(threat), FLEE_SPEED, now);
            }
            case TRAVEL -> {
                // A new post starts its own stall clock; a fresh controller has no progress history yet.
                if (post == null) { post = choosePost(order); progress = actor.position(); progressAt = now; }
                Vec3 goal = Vec3.atBottomCenterOf(post);
                boolean stalled = now - progressAt >= STALL;
                if (actor.position().subtract(goal).horizontalDistanceSqr() <= 2.25 || stalled) {
                    setPhase(Phase.WATCH, now);
                    actor.recordDecision("MAEVE_SCRIBE_WATCH", null, "at=" + actor.blockPosition() + " post=" + post + " watch="
                            + order.watchLabel() + "@" + order.watch() + " stalled=" + stalled + " " + postReport);
                } else walkTo(post, WALK_SPEED, now);
            }
            case WATCH -> {
                hold(watchPoint(order));
                if (now - watchedFrom >= WATCH) depart("WATCH_COMPLETE", now);
            }
            case DEPART -> {
                if (nearestPlayer(GONE_RADIUS, false) == null || now - departedAt >= DEPART_LIMIT && nearestPlayer(16, false) == null) {
                    actor.recordDecision("MAEVE_SCRIBE_GONE", null, "reason=" + departure);
                    MaeveDirector.scribeEnded(actor, departure); actor.discard(); return true;
                }
                Vec3 from = threat != null ? threat : post != null ? Vec3.atCenterOf(post) : actor.position().add(1, 0, 0);
                walk(actor.position().subtract(from), WALK_SPEED, now);
            }
        }
        return true;
    }

    /** Presentation only: it writes on its slate while watching from its post, never while moving or defending. */
    boolean writing() {
        return phase == Phase.WATCH && actor.isScribe() && actor.isAlive() && !actor.isNoAi() && attacker == null;
    }

    /** Effective damage. Cornered means struck at close range with no safe way out, or flight that has stalled. */
    void damaged(DamageSource source) {
        if (!actor.isScribe() || !(source.getEntity() instanceof LivingEntity hitter) || !hitter.isAlive()) return;
        long now = now();
        if (defending() && hitter.getUUID().equals(attacker)) { defendUntil = now + DEFEND; return; }
        boolean close = actor.distanceToSqr(hitter) <= CORNERED_RANGE * CORNERED_RANGE;
        boolean stalled = phase == Phase.FLEE && now - progressAt >= FLEE_STALL;
        if (close && (stalled || !escapes(actor.position().subtract(hitter.position())))) {
            if (attacker == null) actor.recordDecision("MAEVE_SCRIBE_CORNERED", null, "attacker=" + hitter.getUUID() + " stalled=" + stalled);
            attacker = hitter.getUUID(); threat = hitter.position(); defendUntil = now + DEFEND;
            actor.setTarget(hitter); actor.resumeAfterReconnaissance();
            return;
        }
        threat = hitter.position(); calmSince = -1;
        if (phase != Phase.DEPART && phase != Phase.FLEE) setPhase(Phase.FLEE, now);
    }

    boolean defending() { return attacker != null && now() < defendUntil; }

    private void depart(String reason, long now) {
        departure = reason; departedAt = now; setPhase(Phase.DEPART, now);
        actor.recordDecision("MAEVE_SCRIBE_DEPART", null, "reason=" + reason);
    }

    private void setPhase(Phase next, long now) {
        if (next == Phase.WATCH) watchedFrom = now;
        phase = next; resetPath(); progress = actor.position(); progressAt = now;
    }

    private void resetPath() {
        if (path != null) path.cleanup();
        path = null; goal = null; waypoint = null; replanAt = 0;
    }

    /** A post on a ring around the remembered watch point: open sky, standable, loaded, away from known heat. */
    private BlockPos choosePost(MaeveDirector.ScribeOrder order) {
        if (order == null || order.watch() == null) { postReport = "route"; return actor.blockPosition(); }
        BlockPos target = order.watch(), best = null; boolean bestVisible = false; double bestDistance = Double.MAX_VALUE;
        int unloaded = 0, noFooting = 0, covered = 0, heat = 0;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            BlockPos column = target.offset((int) Math.round(Math.cos(angle) * WATCH_RADIUS), 0, (int) Math.round(Math.sin(angle) * WATCH_RADIUS));
            String reject = "noFooting";
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos feet = column.above(dy);
                if (!actor.level().hasChunkAt(feet)) { reject = "unloaded"; continue; }
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet) == null) continue;
                if (!actor.level().canSeeSky(feet)) { reject = "covered"; continue; }
                if (!HeaterRegistry.nearby(actor.level(), feet, 8, 1).isEmpty()) { reject = "heat"; continue; }
                reject = null;
                boolean visible = sees(Vec3.atBottomCenterOf(feet).add(0, actor.getEyeHeight(), 0), Vec3.atCenterOf(target));
                double distance = feet.distSqr(actor.blockPosition());
                if (best == null || visible && !bestVisible || visible == bestVisible && distance < bestDistance) {
                    best = feet; bestVisible = visible; bestDistance = distance;
                }
                break;
            }
            if ("unloaded".equals(reject)) unloaded++; else if ("covered".equals(reject)) covered++;
            else if ("heat".equals(reject)) heat++; else if (reject != null) noFooting++;
        }
        postReport = "rejected unloaded=" + unloaded + " noFooting=" + noFooting + " covered=" + covered + " heat=" + heat + " visible=" + bestVisible;
        return best == null ? actor.blockPosition() : best;
    }

    private Vec3 watchPoint(MaeveDirector.ScribeOrder order) {
        if (order != null && actor.level().getPlayerByUUID(order.subject()) instanceof ServerPlayer subject && subject.isAlive()
                && !subject.isSpectator() && actor.distanceToSqr(subject) <= WATCH_RANGE * WATCH_RANGE && actor.hasLineOfSight(subject))
            return subject.getEyePosition();
        return order == null || order.watch() == null ? null : Vec3.atCenterOf(order.watch());
    }

    private void hold(Vec3 look) {
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        if (look != null) face(look);
    }

    /** Away from (or toward) a direction: a destination 12 blocks out, walked with the observed-walk planner. */
    private void walk(Vec3 direction, double speed, long now) {
        Vec3 away = direction.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        BlockPos destination = BlockPos.containing(actor.position().add(away.normalize().scale(12)));
        if (goal == null || now >= replanAt) resetPath();
        walkTo(destination, speed, now);
    }

    /**
     * The same loaded, observed-walk route the pawn and scout executors use: partial surfaces such as snow layers
     * are standable, it never places or breaks blocks, and an unreachable route simply stalls in place.
     */
    private void walkTo(BlockPos destination, double speed, long now) {
        if (progress == null || actor.position().distanceToSqr(progress) > .25) { progress = actor.position(); progressAt = now; }
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        if (waypoint != null && actor.position().subtract(waypoint).horizontalDistanceSqr() > .04) { move(waypoint, speed); return; }
        waypoint = null;
        BlockPos start = walkingStart();
        if (path == null || start.equals(goal)) {
            goal = segment(destination);
            if (goal == null) return;
            path = new DStarLitePathfinder(); path.configureObservedWalk(List.of());
            path.initialize(goal, start, actor.level()); replanAt = now + 40;
        }
        path.updateStart(start);
        if (!path.computePartial(80, actor.level())) return;
        var step = path.getNextStep(start, actor.level());
        if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) { resetPath(); return; }
        waypoint = ArchitectWalkGeometry.observedStandingPosition(actor.level(), step.pos());
        if (waypoint != null) move(waypoint, speed);
    }

    /** Nearest standable cell up to 12 blocks toward the destination. */
    private BlockPos segment(BlockPos destination) {
        Vec3 delta = Vec3.atBottomCenterOf(destination).subtract(actor.position());
        double scale = Math.min(1, 12 / Math.max(1, delta.horizontalDistance()));
        BlockPos center = BlockPos.containing(actor.getX() + delta.x * scale, actor.getY(), actor.getZ() + delta.z * scale);
        for (int radius = 0; radius <= 2; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos candidate = center.offset(x, dy, z);
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), candidate) != null) return candidate;
            }
        }
        return null;
    }

    /** Collision can raise the feet before the center leaves a lower partial surface. */
    private BlockPos walkingStart() {
        BlockPos feet = actor.blockPosition();
        if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet) != null) return feet;
        Vec3 lower = ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet.below());
        return lower != null && Math.abs(actor.getY() - lower.y) <= .6 ? feet.below() : feet;
    }

    private void move(Vec3 next, double speed) {
        Vec3 delta = next.subtract(actor.position()); double length = delta.horizontalDistance();
        face(next.add(0, actor.getEyeHeight(), 0));
        if (length < .01 || !actor.onGround()) return;
        double pace = Math.min(speed, length);
        actor.setDeltaMovement(delta.x / length * pace, actor.getDeltaMovement().y, delta.z / length * pace);
    }

    /** Any standable neighbouring step away from the attacker, judged like an observed walk transition. */
    private boolean escapes(Vec3 direction) {
        Vec3 away = direction.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) return false;
        BlockPos start = walkingStart();
        for (int angle : HEADINGS) {
            Vec3 heading = away.normalize().yRot((float) Math.toRadians(angle));
            BlockPos cell = start.offset((int) Math.round(heading.x), 0, (int) Math.round(heading.z));
            if (cell.equals(start)) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos next = cell.above(dy);
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), next) != null
                        && (Math.abs(next.getX() - start.getX()) + Math.abs(next.getZ() - start.getZ()) != 1
                        || ArchitectWalkGeometry.canObservedWalkTransition(actor.level(), start, next))) return true;
            }
        }
        return false;
    }

    /** Local senses only: a visible, eligible player inside the radius. */
    private ServerPlayer nearestPlayer(double radius, boolean visible) {
        ServerPlayer nearest = null;
        for (ServerPlayer player : ((ServerLevel) actor.level()).players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative() || actor.distanceToSqr(player) > radius * radius
                    || visible && !actor.hasLineOfSight(player)) continue;
            if (nearest == null || actor.distanceToSqr(player) < actor.distanceToSqr(nearest)) nearest = player;
        }
        return nearest;
    }

    private boolean sees(Vec3 from, Vec3 to) {
        return actor.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS;
    }

    private void face(Vec3 point) {
        float yaw = (float) (Math.atan2(point.z - actor.getZ(), point.x - actor.getX()) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(point.x, point.y, point.z, 15, 15);
    }

    void clear() {
        resetPath();
        phase = Phase.TRAVEL; post = null; threat = null; progress = null; attacker = null;
        watchedFrom = -1; departedAt = -1; calmSince = -1; defendUntil = 0; departure = null;
    }
}
