package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.HeaterRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * §9.4b local executor. Walks out into the cold, stands at a distance and watches, flees when approached,
 * and fights only when cornered (§9.13a local-defense exception). It never initiates combat.
 * With a remembered opening or shelter it watches that; without one it keeps a stand-off from the subject
 * itself, just outside flee range, and closes in again when the subject draws away (owner, 2026-10-08).
 * It walks with ordinary mob navigation (one-block rises, drops, woods), never placing or breaking blocks.
 * Reload restarts the local watch; the claim's lifetime still bounds the whole appearance.
 */
final class ArchitectScribeController {
    private enum Phase { TRAVEL, WATCH, FLEE, DEPART }
    private static final int[] HEADINGS = {0, 45, -45, 90, -90};
    /** A stalled flight tries each of these turns off straight-away before it counts as cornered. */
    private static final int[] FLEE_TURNS = {0, 45, -45, 90, -90, 135, -135};
    private static final double WATCH_RADIUS = 20, WATCH_RANGE = 48, FLEE_RADIUS = 12, CALM_RADIUS = 20, GONE_RADIUS = 32;
    /** Navigation speed modifiers on the Architect's movement speed: about 2.8 and 4.3 blocks a second. */
    private static final double CORNERED_RANGE = 3.5, WALK_SPEED = .9, FLEE_SPEED = 1.15;
    /** Stand-off from the subject when there is no remembered place: posts at STANDOFF, closes in beyond DRIFT. */
    private static final double STANDOFF = 16, SETTLED = 20, DRIFT = 28, NOTICE = 4;
    private static final long WATCH = 2400, DEFEND = 200, DEPART_LIMIT = 600, STALL = 100, CALM = 40, FLEE_STALL = 30, REPLAN = 40;
    private final ArchitectEntity actor;
    private Phase phase = Phase.TRAVEL;
    private BlockPos post;
    private Vec3 threat, progress;
    private BlockPos navTarget;
    private long progressAt, replanAt, watchedFrom = -1, departedAt = -1, calmSince = -1, defendUntil, turnAt;
    private int turn;
    private boolean repost;
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
            note("DEFENSE_ENDED", "no recent damage; resumes fleeing");
            flee(now);
        }
        var order = MaeveDirector.scribeOrder(actor);
        if (phase != Phase.DEPART && (order == null || !order.dimension().equals(actor.level().dimension().location().toString())))
            depart(order == null ? "ORDER_ENDED" : "DIMENSION_CHANGED", now);
        actor.setTarget(null); actor.setSprinting(false);
        actor.setMaeveHolding(false); actor.setCommitmentAction(false);
        var near = nearestPlayer(FLEE_RADIUS, true);
        if (near == null) near = nearestPlayer(NOTICE, false);
        if (near != null && phase != Phase.DEPART) {
            threat = near.position(); calmSince = -1;
            if (phase != Phase.FLEE) { note("FLEE", "from=" + near.getName().getString() + " dist=" + dist(near)); flee(now); }
        }
        switch (phase) {
            case FLEE -> {
                if (nearestPlayer(CALM_RADIUS, false) == null) {
                    if (calmSince < 0) calmSince = now;
                    if (now - calmSince >= CALM) { post = null; setPhase(Phase.TRAVEL, now); }
                } else calmSince = -1;
                if (now - progressAt >= FLEE_STALL && now >= turnAt) {
                    turn++; turnAt = now + FLEE_STALL; resetPath();
                    note("FLEE_TURN", "at=" + actor.blockPosition() + " turn=" + FLEE_TURNS[turn % FLEE_TURNS.length] + " tried=" + turn);
                }
                Vec3 away = threat == null ? actor.getLookAngle().reverse() : actor.position().subtract(threat);
                walk(away.yRot((float) Math.toRadians(FLEE_TURNS[turn % FLEE_TURNS.length])), FLEE_SPEED, now);
            }
            case TRAVEL -> {
                // A new post starts its own stall clock; a fresh controller has no progress history yet.
                if (post == null) { post = choosePost(order); progress = actor.position(); progressAt = now; }
                Vec3 goal = Vec3.atBottomCenterOf(post);
                boolean stalled = now - progressAt >= STALL;
                if (actor.position().subtract(goal).horizontalDistanceSqr() <= 2.25 || stalled) {
                    setPhase(Phase.WATCH, now);
                    note("WATCH", "at=" + actor.blockPosition() + " post=" + post + " watch="
                            + order.watchLabel() + "@" + order.watch() + " stalled=" + stalled + " " + postReport);
                } else walkTo(post, WALK_SPEED, now);
            }
            case WATCH -> {
                hold(watchPoint(order));
                if (now - watchedFrom >= WATCH) depart("WATCH_COMPLETE", now);
                else if (order.watch() == null && subject(order) instanceof ServerPlayer subject && dist(subject) > DRIFT) {
                    note("CLOSE_IN", "subject dist=" + dist(subject));
                    post = null; repost = true; setPhase(Phase.TRAVEL, now);
                }
            }
            case DEPART -> {
                if (nearestPlayer(GONE_RADIUS, false) == null || now - departedAt >= DEPART_LIMIT && nearestPlayer(16, false) == null) {
                    note("GONE", "reason=" + departure);
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
            if (attacker == null) note("CORNERED", "attacker=" + hitter.getUUID() + " dist=" + dist(hitter) + " stalled=" + stalled);
            attacker = hitter.getUUID(); threat = hitter.position(); defendUntil = now + DEFEND;
            actor.setTarget(hitter); actor.resumeAfterReconnaissance();
            return;
        }
        threat = hitter.position(); calmSince = -1;
        if (phase != Phase.DEPART && phase != Phase.FLEE) { note("FLEE", "struck dist=" + dist(hitter)); flee(now); }
    }

    boolean defending() { return attacker != null && now() < defendUntil; }

    private void depart(String reason, long now) {
        departure = reason; departedAt = now; setPhase(Phase.DEPART, now);
        note("DEPART", "reason=" + reason);
    }

    private void flee(long now) { turn = 0; turnAt = 0; repost = false; setPhase(Phase.FLEE, now); }

    /** Journal and server log alike, so a lab pass shows what the Scribe did without a decision recording. */
    private void note(String event, String detail) {
        actor.recordDecision("MAEVE_SCRIBE_" + event, null, detail);
        FrozenDawn.LOGGER.info("[MACS Scribe] {} scribe={} {}", event, actor.getUUID(), detail);
    }

    private double dist(LivingEntity other) { return Math.round(actor.distanceTo(other) * 10) / 10.0; }

    private ServerPlayer subject(MaeveDirector.ScribeOrder order) {
        return order != null && actor.level().getPlayerByUUID(order.subject()) instanceof ServerPlayer subject && subject.isAlive()
                && !subject.isSpectator() && subject.level() == actor.level() ? subject : null;
    }

    private void setPhase(Phase next, long now) {
        // Closing in on a drifting subject keeps the same watch clock; settling after flight starts a new one.
        if (next == Phase.WATCH) { if (!repost || watchedFrom < 0) watchedFrom = now; repost = false; }
        phase = next; resetPath(); progress = actor.position(); progressAt = now;
    }

    private void resetPath() {
        navTarget = null; replanAt = 0;
        actor.getNavigation().stop();
    }

    /**
     * A post on a ring around the remembered watch point: open sky, standable, loaded, away from known heat.
     * Without one, a ring at the stand-off around the subject (cover allowed, so woods do not strand it), unless it
     * already stands close enough.
     */
    private BlockPos choosePost(MaeveDirector.ScribeOrder order) {
        if (order == null) { postReport = "none"; return actor.blockPosition(); }
        if (order.watch() != null) return ring(order.watch(), WATCH_RADIUS, true);
        var subject = subject(order);
        if (subject == null) { postReport = "route"; return actor.blockPosition(); }
        if (dist(subject) <= SETTLED) { postReport = "standoff dist=" + dist(subject); return actor.blockPosition(); }
        var post = ring(subject.blockPosition(), STANDOFF, false);
        postReport = "standoff " + postReport;
        return post;
    }

    private BlockPos ring(BlockPos target, double radius, boolean sky) {
        BlockPos best = null; boolean bestVisible = false; double bestDistance = Double.MAX_VALUE;
        int unloaded = 0, noFooting = 0, covered = 0, heat = 0;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            BlockPos column = target.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
            String reject = "noFooting";
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos feet = column.above(dy);
                if (!actor.level().hasChunkAt(feet)) { reject = "unloaded"; continue; }
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet) == null) continue;
                if (sky && !actor.level().canSeeSky(feet)) { reject = "covered"; continue; }
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
        if (navTarget != null || !actor.getNavigation().isDone()) resetPath();
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        if (look != null) face(look);
    }

    /** Away from (or toward) a direction: a reachable spot about 16 blocks that way, re-chosen every two seconds. */
    private void walk(Vec3 direction, double speed, long now) {
        track(now);
        if (navTarget != null && now < replanAt && !actor.getNavigation().isDone()) return;
        Vec3 away = direction.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        Vec3 toward = actor.position().add(away.normalize().scale(16));
        Vec3 spot = LandRandomPos.getPosTowards(actor, 16, 7, toward);
        BlockPos destination = spot != null ? BlockPos.containing(spot) : BlockPos.containing(actor.position().add(away.normalize().scale(12)));
        navigate(destination, speed, now);
    }

    /** Ordinary navigation to a fixed destination, re-issued when it ends short. */
    private void walkTo(BlockPos destination, double speed, long now) {
        track(now);
        if (destination.equals(navTarget) && (now < replanAt || !actor.getNavigation().isDone())) return;
        navigate(destination, speed, now);
    }

    private void navigate(BlockPos destination, double speed, long now) {
        navTarget = destination; replanAt = now + REPLAN;
        actor.getNavigation().moveTo(destination.getX() + .5, destination.getY(), destination.getZ() + .5, speed);
    }

    private void track(long now) {
        if (progress == null || actor.position().distanceToSqr(progress) > .25) { progress = actor.position(); progressAt = now; }
    }

    /** The footing in one neighbouring column, from two blocks down to one up, if the body clears the way there. */
    private Vec3 neighbour(BlockPos start, int dx, int dz) {
        for (int dy : new int[]{0, -1, 1, -2}) {
            Vec3 stand = ArchitectWalkGeometry.observedStandingPosition(actor.level(), start.offset(dx, dy, dz));
            if (stand == null || stand.y - actor.getY() > 1.01) continue;
            var box = actor.getBoundingBox().move(0, Math.max(stand.y, actor.getY()) - actor.getY() + .01, 0);
            if (actor.level().noCollision(actor, box.minmax(box.move(stand.x - actor.getX(), 0, stand.z - actor.getZ())))) return stand;
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

    /** Any neighbouring step away from the attacker that the Scribe can take: a drop of up to two or a one-block rise. */
    private boolean escapes(Vec3 direction) {
        Vec3 away = direction.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) return false;
        BlockPos start = walkingStart();
        for (int angle : HEADINGS) {
            Vec3 heading = away.normalize().yRot((float) Math.toRadians(angle));
            int dx = (int) Math.round(heading.x), dz = (int) Math.round(heading.z);
            if ((dx != 0 || dz != 0) && neighbour(start, dx, dz) != null) return true;
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
        watchedFrom = -1; departedAt = -1; calmSince = -1; defendUntil = 0; departure = null; turn = 0; turnAt = 0; repost = false;
    }
}
