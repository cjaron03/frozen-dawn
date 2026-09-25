package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.maeve.MaeveDirector;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local executor. Receives bounded historical hints; never reads the belief store. */
final class ArchitectCommitmentController {
    private final ArchitectEntity architect;
    private final ArchitectBlockBreaker breaker;
    private final ArchitectSpatialCommitment spatial;
    private final ArchitectShieldController shield;
    private final ArchitectMantletController mantlet;
    ArchitectShieldController shield() { return shield; }
    private boolean wasActive;
    private long nextPlan;

    ArchitectCommitmentController(ArchitectEntity architect, ArchitectBlockBreaker breaker) {
        this.architect = architect;
        this.breaker = breaker;
        spatial = new ArchitectSpatialCommitment(architect);
        shield = new ArchitectShieldController(architect);
        mantlet = new ArchitectMantletController(architect);
    }

    boolean tick(LivingEntity localTarget) {
        long now = architect.getServer().overworld().getGameTime();
        var directive = MaeveDirector.positionDirective(architect);
        // A finished shield may not leak into a new encounter's selected counter.
        if (directive == null && shield.active()) clear();
        if (directive == null && now >= nextPlan && localTarget instanceof ServerPlayer player) {
            nextPlan = now + 20;
            var hints = MaeveDirector.commitmentHints(architect, player);
            // A freshly spawned actor settles after its first AI tick. Do not let
            // ordinary fortification obstruct the mantlet corridor during that landing.
            if (!architect.onGround() && hints.stream().anyMatch(h -> h.pattern().equals("PLAYER_PREFERS_RANGED") && h.confidence() >= .90)) nextPlan = now + 1;
            if (!hints.isEmpty() && safeToCommit() && architect.onGround()) {
                var candidates = new ArrayList<>(candidates(hints, player));
                candidates.addAll(MaeveDirector.spatialCandidates(architect, player, hints));
                if (MaeveDirector.chooseCommitment(architect, player, candidates)) directive = MaeveDirector.positionDirective(architect);
            }
        }
        if (directive == null) {
            if (wasActive) clear();
            return false;
        }
        boolean swordGuard = directive.pattern().equals(ArchitectShieldController.PATTERN);
        if (!(swordGuard ? safeEnvironment() : safeToCommit())
                || !directive.evidence().dimension().equals(architect.level().dimension().location().toString())) {
            release("LOCAL_SAFETY_RELEASE");
            return false;
        }
        wasActive = true;
        if (swordGuard) {
            boolean guarding = shield.tick(directive, localTarget, now);
            // A lowered shield yields to ordinary pursuit, including queued mining.
            if (guarding) breaker.clearTarget();
            return guarding;
        }
        breaker.clearTarget();
        if (directive.advancingCover()) return mantlet.tick(directive, localTarget, now);
        // Environmental displacement may settle back to the point. Effective
        // damage has its own final-event release, after evidence is recorded.
        if (!architect.onGround()) {
            architect.setMaeveHolding(false);
            if (Math.abs(architect.getY() - directive.position().getY()) > 2) { release("LOCAL_FALL_RISK"); return false; }
            architect.getNavigation().stop();
            return true;
        }
        stopMotion();
        architect.setSprinting(false);
        if (directive.spatial() != null) {
            spatial.tick(directive, localTarget, now);
            return true;
        }
        Vec3 goal = directive.cover() == null ? Vec3.atBottomCenterOf(directive.position())
                : coverStandingPoint(directive.position());
        if (architect.position().distanceToSqr(goal) > 0.36D) {
            if (!safeWalk(goal)) { release("LOCAL_ROUTE_UNSAFE"); return false; }
            architect.setMaeveHolding(false);
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
            architect.setMaeveHolding(true);
            Vec3 anchor = directive.evidence().position().getCenter();
            if (directive.pattern().equals("PLAYER_PURSUES_WITHDRAWING_ARCHITECT") && localTarget != null
                    && architect.hasLineOfSight(localTarget)) anchor = localTarget.position();
            // Face the inherited event with the whole body. A head-only glance
            // looked identical to ordinary observation in the informed replays.
            float toward = (float) (Mth.atan2(anchor.z - architect.getZ(), anchor.x - architect.getX())
                    * (180.0D / Math.PI)) - 90.0F;
            float yaw = Mth.approachDegrees(architect.getYRot(), toward, 12.0F);
            architect.setYRot(yaw);
            architect.setYBodyRot(yaw);
            architect.setYHeadRot(yaw);
            architect.getLookControl().setLookAt(anchor.x, anchor.y + 0.5D, anchor.z, 12, 12);
            if (arrival && directive.cover() != null) {
                int height = com.frozendawn.entity.architect.ArchitectCoverGeometry.PILLAR_HEIGHT;
                if (!clearCover(directive.cover()) || architect.placeCoverPillar(directive.cover()) != height) {
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

    private List<MaeveDirector.PositionCandidate> candidates(List<MaeveDirector.CommitmentHint> hints, ServerPlayer player) {
        var candidates = new ArrayList<MaeveDirector.PositionCandidate>();
        for (var hint : hints) {
            if (hint.confidence() < 0.75D) continue;
            if (hint.pattern().equals(ArchitectShieldController.PATTERN)) {
                if (architect.getBrainAction() != ArchitectEntity.ACTION_RETREAT && !architect.isDrinkingPotion()
                        && shield.canEquip(architect.getServer().overworld().getGameTime()))
                    candidates.add(new MaeveDirector.PositionCandidate(hint.pattern(), architect.blockPosition(), null, 1.5));
                continue;
            }
            Vec3 anchor = Vec3.atBottomCenterOf(hint.evidence().position());
            Vec3 away = architect.position().subtract(anchor).multiply(1, 0, 1);
            if (away.lengthSqr() < 1) continue;
            away = away.normalize();
            if (hint.pattern().equals("PLAYER_PREFERS_RANGED")) {
                if (hint.confidence() >= .90) {
                    var advancing = mantlet.candidate(hint);
                    if (advancing != null) candidates.add(advancing);
                }
                BlockPos position = architect.blockPosition();
                Vec3 feet = coverStandingPoint(position);
                BlockPos cover = com.frozendawn.entity.architect.ArchitectCoverGeometry.find(
                        architect, feet, anchor.add(0, 1.62, 0));
                Vec3 sideways = new Vec3(-away.z, 0, away.x);
                boolean canAbandon = safeWalk(architect.position().add(away.scale(2)))
                        || safeWalk(architect.position().add(sideways.scale(2)))
                        || safeWalk(architect.position().subtract(sideways.scale(2)));
                if (cover != null && safeWalk(feet) && canAbandon && clearCover(cover)) {
                    candidates.add(new MaeveDirector.PositionCandidate(hint.pattern(), position, cover, 2));
                }
            } else if (hint.pattern().equals("PLAYER_PURSUES_WITHDRAWING_ARCHITECT")) {
                Vec3 localAway = architect.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                BlockPos position = BlockPos.containing(architect.position().add(localAway.scale(5)));
                if (safeWalk(Vec3.atBottomCenterOf(position))) candidates.add(new MaeveDirector.PositionCandidate(
                        hint.pattern(), position, null, architect.position().distanceTo(Vec3.atBottomCenterOf(position))));
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
        return safeEnvironment() && architect.getHealth() > architect.getMaxHealth() * 0.3F;
    }

    private boolean safeEnvironment() {
        return architect.isAlive() && !architect.isNoAi()
                && !architect.isInWaterOrBubble() && !architect.isOnFire();
    }

    private boolean clearCover(BlockPos pos) {
        int height = com.frozendawn.entity.architect.ArchitectCoverGeometry.PILLAR_HEIGHT;
        return architect.getTacticalIceCount() + height <= architect.getMaxTacticalIce()
                && com.frozendawn.entity.architect.ArchitectCoverGeometry.canPlacePillar(architect, architect.position(), pos);
    }

    private Vec3 coverStandingPoint(BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        if (!architect.level().hasChunkAt(pos)) return center;
        var state = architect.level().getBlockState(pos);
        if (!state.is(Blocks.SNOW)) return center;
        var shape = state.getCollisionShape(architect.level(), pos);
        return shape.isEmpty() ? center : center.add(0, shape.max(Direction.Axis.Y), 0);
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
        spatial.clear();
        shield.clear();
        mantlet.clear();
        wasActive = false;
        architect.setMaeveHolding(false);
        stopMotion();
        architect.triggerReeval();
    }
}
