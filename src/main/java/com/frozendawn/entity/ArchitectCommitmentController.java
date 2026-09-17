package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.maeve.MaeveDirector;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local executor. Receives at most two historical hints; never reads the belief store. */
final class ArchitectCommitmentController {
    private final ArchitectEntity architect;
    private final ArchitectBlockBreaker breaker;
    private boolean wasActive;
    private long nextPlan;

    ArchitectCommitmentController(ArchitectEntity architect, ArchitectBlockBreaker breaker) {
        this.architect = architect;
        this.breaker = breaker;
    }

    boolean tick(LivingEntity localTarget) {
        long now = architect.getServer().overworld().getGameTime();
        var directive = MaeveDirector.positionDirective(architect);
        if (directive == null && now >= nextPlan && localTarget instanceof ServerPlayer player) {
            nextPlan = now + 20;
            var hints = MaeveDirector.commitmentHints(architect, player);
            if (!hints.isEmpty() && safeToCommit() && architect.onGround()) {
                var candidates = candidates(hints);
                if (MaeveDirector.chooseCommitment(architect, player, candidates)) directive = MaeveDirector.positionDirective(architect);
            }
        }
        if (directive == null) {
            if (wasActive) clear();
            return false;
        }
        if (!safeToCommit() || !directive.evidence().dimension().equals(architect.level().dimension().location().toString())) {
            release("LOCAL_SAFETY_RELEASE");
            return false;
        }
        wasActive = true;
        breaker.clearTarget();
        // Ordinary melee knockback is a possible contradiction, not a reason to
        // instantly abandon the bet. Let gravity settle it, then return to the point.
        if (!architect.onGround()) {
            if (Math.abs(architect.getY() - directive.position().getY()) > 2) { release("LOCAL_FALL_RISK"); return false; }
            architect.getNavigation().stop();
            return true;
        }
        stopMotion();
        architect.setSprinting(false);
        Vec3 goal = Vec3.atBottomCenterOf(directive.position());
        if (architect.position().distanceToSqr(goal) > 0.36D) {
            if (!safeWalk(goal)) { release("LOCAL_ROUTE_UNSAFE"); return false; }
            architect.setCommitmentAction(false);
            Vec3 delta = goal.subtract(architect.position());
            double length = delta.horizontalDistance();
            double speed = Math.min(0.16D, length);
            architect.setDeltaMovement(delta.x / length * speed, architect.getDeltaMovement().y, delta.z / length * speed);
            architect.getLookControl().setLookAt(goal.x, goal.y + 1, goal.z, 15, 15);
        } else {
            boolean arrival = directive.arrivedAt() < 0;
            MaeveDirector.commitmentArrived(architect);
            stopMotion();
            architect.setCommitmentAction(true);
            Vec3 anchor = directive.evidence().position().getCenter();
            architect.getLookControl().setLookAt(anchor.x, anchor.y + 0.5D, anchor.z, 12, 12);
            if (arrival && directive.cover() != null) {
                if (!clearCover(directive.cover()) || !architect.placeTacticalIce(directive.cover())
                        || !architect.placeTacticalIce(directive.cover().above())) {
                    release("LOCAL_COVER_UNAVAILABLE");
                    return false;
                }
            }
            // Defend the held position using current local sight; do not chase a broken bet.
            if (localTarget != null && architect.distanceToSqr(localTarget) < 2.8D * 2.8D
                    && architect.hasLineOfSight(localTarget) && architect.attackAnim == 0) {
                architect.swing(InteractionHand.MAIN_HAND);
                architect.doHurtTarget(localTarget);
            }
        }
        return true;
    }

    private List<MaeveDirector.PositionCandidate> candidates(List<MaeveDirector.CommitmentHint> hints) {
        var candidates = new ArrayList<MaeveDirector.PositionCandidate>();
        for (var hint : hints) {
            if (hint.confidence() < 0.75D) continue;
            Vec3 anchor = Vec3.atBottomCenterOf(hint.evidence().position());
            Vec3 away = architect.position().subtract(anchor).multiply(1, 0, 1);
            if (away.lengthSqr() < 1) continue;
            away = away.normalize();
            if (hint.pattern().equals("PLAYER_PREFERS_RANGED")) {
                BlockPos position = architect.blockPosition();
                BlockPos cover = BlockPos.containing(Vec3.atBottomCenterOf(position).subtract(away.scale(2)));
                Vec3 sideways = new Vec3(-away.z, 0, away.x);
                boolean canAbandon = safeWalk(architect.position().add(away.scale(2)))
                        || safeWalk(architect.position().add(sideways.scale(2)))
                        || safeWalk(architect.position().subtract(sideways.scale(2)));
                if (safeWalk(Vec3.atBottomCenterOf(position)) && canAbandon && clearCover(cover)) {
                    candidates.add(new MaeveDirector.PositionCandidate(hint.pattern(), position, cover, 2));
                }
            } else if (hint.pattern().equals("PLAYER_USES_RECOVERY_UNDER_COVER")) {
                var recovery = recoveryCandidate(hint.pattern(), anchor, away);
                if (recovery != null) candidates.add(recovery);
            }
        }
        return List.copyOf(candidates);
    }

    private MaeveDirector.PositionCandidate recoveryCandidate(String pattern, Vec3 anchor, Vec3 away) {
        Vec3 standOff = anchor.add(away.scale(3));
        Vec3 across = new Vec3(-away.z, 0, away.x).scale(2);
        MaeveDirector.PositionCandidate best = null;
        // A point on the ordinary approach line can already be under the actor's
        // feet. Try two lateral watches of the same witnessed event instead.
        for (int side : new int[]{1, -1}) {
            BlockPos position = BlockPos.containing(standOff.add(across.scale(side)));
            Vec3 goal = Vec3.atBottomCenterOf(position);
            double distance = architect.position().distanceTo(goal);
            if (distance >= 2 && distance <= 6 && safeWalk(goal)
                    && (best == null || distance < best.recoveryCost())) {
                best = new MaeveDirector.PositionCandidate(pattern, position, null, distance);
            }
        }
        return best;
    }

    private boolean safeToCommit() {
        return architect.isAlive() && !architect.isNoAi()
                && !architect.isInWaterOrBubble() && !architect.isOnFire()
                && architect.getHealth() > architect.getMaxHealth() * 0.3F;
    }

    private boolean clearCover(BlockPos pos) {
        if (architect.getTacticalIceCount() + 2 > architect.getMaxTacticalIce()
                || !architect.level().hasChunkAt(pos) || !architect.level().hasChunkAt(pos.above())) return false;
        if (!architect.level().getBlockState(pos).isAir() || !architect.level().getBlockState(pos.above()).isAir()) return false;
        var occupants = new ArrayList<net.minecraft.world.entity.Entity>();
        ((net.minecraft.server.level.ServerLevel) architect.level()).getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                new AABB(pos).expandTowards(0, 1, 0), entity -> entity != architect, occupants, 1);
        return occupants.isEmpty();
    }

    /** Straight, level, short paths only. No breach, scaffold, navigation search, or chunk loads. */
    private boolean safeWalk(Vec3 goal) {
        double distance = architect.position().distanceTo(goal);
        if (distance > 6.5D || Math.abs(goal.y - architect.getY()) > 0.1D) return false;
        int steps = Math.max(1, (int) Math.ceil(distance * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = architect.position().lerp(goal, (double) i / steps);
            AABB body = architect.getBoundingBox().move(point.subtract(architect.position())).deflate(0.01D);
            for (int x = ((int) Math.floor(body.minX) >> 4); x <= ((int) Math.floor(body.maxX) >> 4); x++) {
                for (int z = ((int) Math.floor(body.minZ) >> 4); z <= ((int) Math.floor(body.maxZ) >> 4); z++) {
                    if (!architect.level().hasChunk(x, z)) return false;
                }
            }
            BlockPos feet = BlockPos.containing(point);
            var ground = architect.level().getBlockState(feet.below());
            var foot = architect.level().getBlockState(feet);
            if (!ground.isFaceSturdy(architect.level(), feet.below(), Direction.UP)
                    || ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)
                    || !foot.getFluidState().isEmpty() || foot.is(BlockTags.FIRE)
                    || !architect.level().noCollision(architect, body)) return false;
        }
        return true;
    }

    private void stopMotion() {
        architect.getNavigation().stop();
        architect.getMoveControl().setWantedPosition(architect.getX(), architect.getY(), architect.getZ(), 0);
        architect.setDeltaMovement(0, architect.getDeltaMovement().y, 0);
        architect.setSpeed(0);
        architect.setZza(0);
        architect.setXxa(0);
    }

    private void release(String reason) {
        MaeveDirector.releaseCommitment(architect, reason);
        clear();
    }

    void clear() {
        wasActive = false;
        stopMotion();
        architect.triggerReeval();
    }
}
