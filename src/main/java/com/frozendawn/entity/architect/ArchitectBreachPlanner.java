package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Planning helpers for selecting breach targets during approach fallback paths.
 */
public final class ArchitectBreachPlanner {

    private static final double CONTACT_BREACH_HORIZONTAL_RANGE = 4.25D;
    private static final double CONTACT_BREACH_VERTICAL_RANGE = 3.0D;

    private ArchitectBreachPlanner() {
    }

    @Nullable
    public static BlockPos findDropInBreakTarget(
            LivingEntity actor,
            @Nullable LivingEntity target,
            BlockPos stepPos,
            Predicate<BlockPos> isBreakableBlock
    ) {
        BlockPos below = actor.blockPosition().below();
        if (isBreakableBlock.test(below)) {
            return below;
        }

        BlockPos stepBelow = stepPos.below();
        if (isBreakableBlock.test(stepBelow)) {
            return stepBelow;
        }

        BlockPos fallback = findBreakableWallBlock(actor, target, isBreakableBlock);
        if (fallback != null && fallback.getY() == actor.blockPosition().getY() - 1) {
            return fallback;
        }

        return null;
    }

    @Nullable
    public static BlockPos findBreakableWallBlock(
            LivingEntity actor,
            @Nullable LivingEntity target,
            Predicate<BlockPos> isBreakableBlock
    ) {
        if (target == null) {
            return null;
        }

        // Priority 1: Dig-down when target is below and horizontally close enough.
        double dxToTarget = target.getX() - actor.getX();
        double dzToTarget = target.getZ() - actor.getZ();
        double horizontalDistToTarget = Math.sqrt(dxToTarget * dxToTarget + dzToTarget * dzToTarget);
        double verticalDropToTarget = actor.getY() - target.getY();
        if (verticalDropToTarget >= 2.0 && horizontalDistToTarget <= 6.0) {
            BlockPos below = actor.blockPosition().below();
            if (isBreakableBlock.test(below)) {
                return below;
            }

            BlockPos closest = null;
            double closestDist = Double.MAX_VALUE;
            for (int ox = -3; ox <= 3; ox++) {
                for (int oz = -3; oz <= 3; oz++) {
                    if (ox == 0 && oz == 0) {
                        continue;
                    }
                    BlockPos candidate = below.offset(ox, 0, oz);
                    if (isBreakableBlock.test(candidate)) {
                        double d = actor.position().distanceToSqr(
                                candidate.getX() + 0.5,
                                candidate.getY() + 0.5,
                                candidate.getZ() + 0.5);
                        if (d < closestDist) {
                            closestDist = d;
                            closest = candidate;
                        }
                    }
                }
            }
            if (closest != null) {
                return closest;
            }
        }

        return findDirectWallBlock(actor, target, isBreakableBlock);
    }

    public static boolean shouldAttemptContactBreach(
            boolean hasLineOfSight,
            boolean alreadyBreaking,
            double horizontalDistance,
            double verticalDistance
    ) {
        return !hasLineOfSight
                && !alreadyBreaking
                && horizontalDistance <= CONTACT_BREACH_HORIZONTAL_RANGE
                && verticalDistance <= CONTACT_BREACH_VERTICAL_RANGE;
    }

    @Nullable
    public static BlockPos findDirectWallBlock(
            LivingEntity actor,
            @Nullable LivingEntity target,
            Predicate<BlockPos> isBreakableBlock
    ) {
        if (target == null) {
            return null;
        }

        // Prefer a body-height opening directly toward the target.
        {
            double dx = target.getX() - actor.getX();
            double dz = target.getZ() - actor.getZ();
            BlockPos feet = actor.blockPosition();
            BlockPos toward;
            if (Math.abs(dx) > Math.abs(dz)) {
                toward = feet.offset(dx > 0 ? 1 : -1, 0, 0);
            } else {
                toward = feet.offset(0, 0, dz > 0 ? 1 : -1);
            }
            if (isBreakableBlock.test(toward)) {
                return toward;
            }
            BlockPos towardHead = toward.above();
            if (isBreakableBlock.test(towardHead)) {
                return towardHead;
            }
        }

        // Raycast fallback from actor toward target.
        Vec3 start = actor.position().add(0, actor.getEyeHeight() * 0.5, 0);
        Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(start).normalize();

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= 10; i++) {
            Vec3 point = start.add(dir.scale(i));
            probe.set((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
            if (isBreakableBlock.test(probe)) {
                return probe.immutable();
            }
        }
        return null;
    }
}
