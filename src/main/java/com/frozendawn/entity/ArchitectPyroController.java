package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.architect.ArchitectWalkGeometry;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TemperatureManager;
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
 * §9.4a local executor. Walks to a cold post on a ring around the suspected home, stands still and stares,
 * looks with thermal sight, then leaves. It never closes on heat and never initiates combat. Struck, it defends
 * itself under the §9.13a local-defense exception and then leaves. Every look is uploaded the moment it is taken,
 * so killing it afterwards undoes nothing. The first look waits a while, which is the window to stop it.
 * Reload restarts the local watch; the claim's lifetime still bounds the whole appearance.
 */
final class ArchitectPyroController {
    private enum Phase { TRAVEL, WATCH, DEPART }
    private static final double POST_RADIUS = 40, WALK_SPEED = .9, GONE_RADIUS = 32;
    /** A post this close to anything warm is rejected: it stays out in the cold. */
    private static final int COLD_CLEARANCE = 12, BLOCK_CLEARANCE = 4;
    private static final long FIRST_LOOK = 200, LOOK = 100, WATCH = 1200, DEFEND = 200, DEPART_LIMIT = 600, STALL = 100, REPLAN = 40;
    private final ArchitectEntity actor;
    private Phase phase = Phase.TRAVEL;
    private BlockPos post, navTarget;
    private Vec3 progress;
    private long progressAt, replanAt, watchedFrom = -1, nextLook, departedAt = -1, defendUntil;
    private UUID attacker;
    private String departure, postReport = "none";

    ArchitectPyroController(ArchitectEntity actor) { this.actor = actor; }
    private long now() { return actor.getServer().overworld().getGameTime(); }

    boolean tick() {
        if (!actor.isPyro()) return false;
        long now = now();
        if (now < defendUntil) return false;
        if (attacker != null) {
            attacker = null; actor.setTarget(null);
            note("DEFENSE_ENDED", "no recent damage; leaves");
            if (phase != Phase.DEPART) depart("DEFENDED", now);
        }
        var order = MaeveDirector.pyroOrder(actor);
        if (phase != Phase.DEPART && (order == null || !order.dimension().equals(actor.level().dimension().location().toString())))
            depart(order == null ? "ORDER_ENDED" : "DIMENSION_CHANGED", now);
        actor.setTarget(null); actor.setSprinting(false);
        actor.setMaeveHolding(false); actor.setCommitmentAction(false);
        switch (phase) {
            case TRAVEL -> {
                if (post == null) { post = ring(order.home()); progress = actor.position(); progressAt = now; }
                boolean stalled = now - progressAt >= STALL;
                if (actor.position().subtract(Vec3.atBottomCenterOf(post)).horizontalDistanceSqr() <= 2.25 || stalled) {
                    phase = Phase.WATCH; watchedFrom = now; nextLook = now + FIRST_LOOK; resetPath();
                    note("WATCH", "at=" + actor.blockPosition() + " post=" + post + " home=" + order.home() + " stalled=" + stalled + " " + postReport);
                } else walkTo(post, now);
            }
            case WATCH -> {
                hold(Vec3.atCenterOf(order.home()));
                if (now >= nextLook) {
                    boolean last = now - watchedFrom >= WATCH;
                    var scan = MaeveDirector.pyroObserve(actor, last);
                    note("LOOK", scan == null ? "no order" : "sources=" + scan.sources() + " seen=" + scan.sightings()
                            + " complete=" + scan.complete() + " last=" + last + (scan.cold() ? " cold" : ""));
                    nextLook = now + LOOK;
                    if (last) depart("WATCH_COMPLETE", now);
                }
            }
            case DEPART -> {
                if (nearestPlayer(GONE_RADIUS) == null || now - departedAt >= DEPART_LIMIT && nearestPlayer(16) == null) {
                    note("GONE", "reason=" + departure);
                    MaeveDirector.pyroEnded(actor, departure); actor.discard(); return true;
                }
                Vec3 from = order != null ? Vec3.atCenterOf(order.home()) : post != null ? Vec3.atCenterOf(post) : actor.position().add(1, 0, 0);
                walk(actor.position().subtract(from), now);
            }
        }
        return true;
    }

    /** Presentation only: the hand-to-chin stare at its post, never while moving or defending. */
    boolean staring() {
        return phase == Phase.WATCH && actor.isPyro() && actor.isAlive() && !actor.isNoAi() && attacker == null;
    }

    /** Effective damage from anything living: ordinary combat against that attacker until it stops, then it leaves. */
    void damaged(DamageSource source) {
        if (!actor.isPyro() || !(source.getEntity() instanceof LivingEntity hitter) || !hitter.isAlive()) return;
        if (attacker == null) note("DEFEND", "attacker=" + hitter.getUUID() + " dist=" + Math.round(actor.distanceTo(hitter) * 10) / 10.0);
        attacker = hitter.getUUID(); defendUntil = now() + DEFEND; resetPath();
        actor.setTarget(hitter); actor.resumeAfterReconnaissance();
    }

    boolean defending() { return attacker != null && now() < defendUntil; }

    private void depart(String reason, long now) {
        departure = reason; departedAt = now; phase = Phase.DEPART; resetPath(); progress = actor.position(); progressAt = now;
        note("DEPART", "reason=" + reason);
    }

    /** Journal and server log alike, so a lab pass shows what the Pyro did without a decision recording. */
    private void note(String event, String detail) {
        actor.recordDecision("MAEVE_PYRO_" + event, null, detail);
        FrozenDawn.LOGGER.info("[MACS Pyro] {} pyro={} {}", event, actor.getUUID(), detail);
    }

    /** A cold, open-sky, standable, loaded post about 40 blocks out; the one that can see the home and is nearest wins. */
    private BlockPos ring(BlockPos home) {
        BlockPos best = null; boolean bestVisible = false; double bestDistance = Double.MAX_VALUE;
        int unloaded = 0, noFooting = 0, covered = 0, warm = 0;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            BlockPos column = home.offset((int) Math.round(Math.cos(angle) * POST_RADIUS), 0, (int) Math.round(Math.sin(angle) * POST_RADIUS));
            String reject = "noFooting";
            for (int dy = 8; dy >= -8; dy--) {
                BlockPos feet = column.above(dy);
                if (!actor.level().hasChunkAt(feet)) { reject = "unloaded"; continue; }
                if (ArchitectWalkGeometry.observedStandingPosition(actor.level(), feet) == null) continue;
                if (!actor.level().canSeeSky(feet)) { reject = "covered"; continue; }
                if (warm(feet)) { reject = "warm"; continue; }
                reject = null;
                boolean visible = sees(Vec3.atBottomCenterOf(feet).add(0, actor.getEyeHeight(), 0), Vec3.atCenterOf(home));
                double distance = feet.distSqr(actor.blockPosition());
                if (best == null || visible && !bestVisible || visible == bestVisible && distance < bestDistance) {
                    best = feet; bestVisible = visible; bestDistance = distance;
                }
                break;
            }
            if ("unloaded".equals(reject)) unloaded++; else if ("covered".equals(reject)) covered++;
            else if ("warm".equals(reject)) warm++; else if (reject != null) noFooting++;
        }
        postReport = "rejected unloaded=" + unloaded + " noFooting=" + noFooting + " covered=" + covered + " warm=" + warm + " visible=" + bestVisible;
        return best == null ? actor.blockPosition() : best;
    }

    private boolean warm(BlockPos feet) {
        if (!HeaterRegistry.nearby(actor.level(), feet, COLD_CLEARANCE, 1).isEmpty()) return true;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-BLOCK_CLEARANCE, -BLOCK_CLEARANCE, -BLOCK_CLEARANCE),
                feet.offset(BLOCK_CLEARANCE, BLOCK_CLEARANCE, BLOCK_CLEARANCE))) {
            if (actor.level().hasChunkAt(pos) && TemperatureManager.getAmbientSignatureHeat(actor.level().getBlockState(pos)) > 0) return true;
        }
        return false;
    }

    private void hold(Vec3 look) {
        if (navTarget != null || !actor.getNavigation().isDone()) resetPath();
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        float yaw = (float) (Math.atan2(look.z - actor.getZ(), look.x - actor.getX()) * 180 / Math.PI) - 90;
        actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
        actor.getLookControl().setLookAt(look.x, look.y, look.z, 15, 15);
    }

    private void walk(Vec3 direction, long now) {
        track(now);
        if (navTarget != null && now < replanAt && !actor.getNavigation().isDone()) return;
        Vec3 away = direction.multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        Vec3 spot = LandRandomPos.getPosTowards(actor, 16, 7, actor.position().add(away.normalize().scale(16)));
        navigate(spot != null ? BlockPos.containing(spot) : BlockPos.containing(actor.position().add(away.normalize().scale(12))), now);
    }

    private void walkTo(BlockPos destination, long now) {
        track(now);
        if (destination.equals(navTarget) && (now < replanAt || !actor.getNavigation().isDone())) return;
        navigate(destination, now);
    }

    private void navigate(BlockPos destination, long now) {
        navTarget = destination; replanAt = now + REPLAN;
        actor.getNavigation().moveTo(destination.getX() + .5, destination.getY(), destination.getZ() + .5, WALK_SPEED);
    }

    private void track(long now) {
        if (progress == null || actor.position().distanceToSqr(progress) > .25) { progress = actor.position(); progressAt = now; }
    }

    private void resetPath() {
        navTarget = null; replanAt = 0;
        actor.getNavigation().stop();
    }

    private ServerPlayer nearestPlayer(double radius) {
        for (ServerPlayer player : ((ServerLevel) actor.level()).players()) {
            if (player.isAlive() && !player.isSpectator() && actor.distanceToSqr(player) <= radius * radius) return player;
        }
        return null;
    }

    private boolean sees(Vec3 from, Vec3 to) {
        return actor.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS;
    }

    void clear() {
        resetPath();
        phase = Phase.TRAVEL; post = null; progress = null; attacker = null;
        watchedFrom = -1; nextLook = 0; departedAt = -1; defendUntil = 0; departure = null;
    }
}
