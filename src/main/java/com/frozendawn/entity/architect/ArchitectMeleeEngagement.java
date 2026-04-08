package com.frozendawn.entity.architect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared melee/chase geometry and motion blending rules.
 */
public final class ArchitectMeleeEngagement {

    private ArchitectMeleeEngagement() {
    }

    public static double horizontalDistanceTo(LivingEntity actor, LivingEntity target) {
        double dx = actor.getX() - target.getX();
        double dz = actor.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double verticalDistanceTo(LivingEntity actor, LivingEntity target) {
        return Math.abs(actor.getY() - target.getY());
    }

    public static boolean isWithinMeleeGeometry(
            LivingEntity actor,
            LivingEntity target,
            double horizontalRange,
            double verticalRange
    ) {
        return horizontalDistanceTo(actor, target) <= horizontalRange
                && verticalDistanceTo(actor, target) <= verticalRange;
    }

    public static boolean hasCleanReachableApproachPath(PathNavigation navigation, LivingEntity target) {
        Path path = navigation.createPath(target, 1);
        if (path == null || !path.canReach()) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (path.getNode(i).type == PathType.BLOCKED) {
                return false;
            }
        }
        return true;
    }

    public static boolean canStartMelee(
            LivingEntity actor,
            LivingEntity target,
            boolean hasLineOfSight,
            PathNavigation navigation,
            double engageHorizontalRange,
            double engageVerticalRange,
            double directHorizontalRange,
            double directVerticalRange
    ) {
        if (!hasLineOfSight || !isWithinMeleeGeometry(actor, target, engageHorizontalRange, engageVerticalRange)) {
            return false;
        }
        if (horizontalDistanceTo(actor, target) <= directHorizontalRange
                && verticalDistanceTo(actor, target) <= directVerticalRange) {
            return true;
        }
        return hasCleanReachableApproachPath(navigation, target);
    }

    public static boolean canCommitToMelee(
            LivingEntity actor,
            LivingEntity target,
            boolean hasLineOfSight,
            PathNavigation navigation,
            double commitHorizontalRange,
            double commitVerticalRange,
            double losGraceRange,
            double directHorizontalRange,
            double directVerticalRange
    ) {
        if (!isWithinMeleeGeometry(actor, target, commitHorizontalRange, commitVerticalRange)) {
            return false;
        }
        if (!hasLineOfSight && actor.distanceTo(target) >= losGraceRange) {
            return false;
        }
        if (horizontalDistanceTo(actor, target) <= directHorizontalRange
                && verticalDistanceTo(actor, target) <= directVerticalRange) {
            return true;
        }
        return hasCleanReachableApproachPath(navigation, target);
    }

    public static Vec3 blendCombatHorizontalMotion(
            Vec3 currentDelta,
            boolean onGround,
            double desiredInputX,
            double desiredInputZ,
            double airControlScale,
            double maxHorizontalSpeed
    ) {
        double scale = onGround ? 1.0 : airControlScale;
        Vec3 desired = new Vec3(desiredInputX * scale, 0.0, desiredInputZ * scale);
        double desiredLen = desired.horizontalDistance();
        if (desiredLen > maxHorizontalSpeed) {
            desired = desired.scale(maxHorizontalSpeed / desiredLen);
        }

        Vec3 currentHorizontal = new Vec3(currentDelta.x, 0.0, currentDelta.z).scale(0.35);
        Vec3 blended = currentHorizontal.add(desired.scale(0.65));
        double blendedLen = blended.horizontalDistance();
        if (blendedLen > maxHorizontalSpeed) {
            blended = blended.scale(maxHorizontalSpeed / blendedLen);
        }
        return blended;
    }
}
