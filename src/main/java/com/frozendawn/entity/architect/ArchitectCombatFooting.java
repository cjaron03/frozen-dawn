package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps voluntary combat motion on supported ground, including slabs and scaffold ice. */
public final class ArchitectCombatFooting {
    private static final double MAX_STEP_DOWN = 0.6;
    private static final double AIRBORNE_SUPPORT_DEPTH = 1.5;
    private static final double LOOK_AHEAD_TICKS = 3.0;
    private static final double INSET = 0.04;

    private ArchitectCombatFooting() { }

    /** A blocked forward combat nudge must hand back to navigation, which can step/jump. */
    public static boolean needsNavigatedApproach(LivingEntity actor, LivingEntity target) {
        Vec3 forward = new Vec3(target.getX() - actor.getX(), 0, target.getZ() - actor.getZ());
        if (forward.horizontalDistanceSqr() < 1.0e-8) return false;
        forward = forward.normalize().scale(0.12);
        return constrain(actor, forward).distanceToSqr(forward) > 1.0e-8;
    }

    public static Vec3 constrain(LivingEntity actor, Vec3 desired) {
        if (desired.horizontalDistanceSqr() < 1.0e-10) return desired;
        double referenceY = actor.getY();
        if (!actor.onGround()) {
            // During a jump compare landing surfaces, not the actor's temporarily raised feet.
            referenceY = supportHeight(actor, actor.getX(), actor.getZ(), actor.getY(), AIRBORNE_SUPPORT_DEPTH);
            if (!Double.isFinite(referenceY)) return Vec3.ZERO;
        }
        if (supportedMotion(actor, desired, referenceY)) return desired;
        Vec3 alongX = new Vec3(desired.x, 0, 0), alongZ = new Vec3(0, 0, desired.z);
        boolean safeX = Math.abs(desired.x) > 1.0e-6 && supportedMotion(actor, alongX, referenceY);
        boolean safeZ = Math.abs(desired.z) > 1.0e-6 && supportedMotion(actor, alongZ, referenceY);
        if (safeX && safeZ) return Math.abs(desired.x) >= Math.abs(desired.z) ? alongX : alongZ;
        if (safeX) return alongX;
        if (safeZ) return alongZ;
        return Vec3.ZERO;
    }

    private static boolean supportedMotion(LivingEntity actor, Vec3 motion, double referenceY) {
        AABB body = actor.getBoundingBox();
        // Check the path as well as the endpoint so narrow holes cannot be skipped.
        for (int step = 1; step <= (int) LOOK_AHEAD_TICKS; step++) {
            AABB next = body.move(motion.x * step, 0, motion.z * step);
            for (var collision : actor.level().getBlockCollisions(actor, next.deflate(1.0e-5))) {
                if (!collision.isEmpty()) return false;
            }
            double[] xs = {next.minX + INSET, next.maxX - INSET};
            double[] zs = {next.minZ + INSET, next.maxZ - INSET};
            for (double x : xs) for (double z : zs) {
                if (!Double.isFinite(supportHeight(actor, x, z, referenceY + 0.05, MAX_STEP_DOWN + 0.05))) return false;
            }
            if (!Double.isFinite(supportHeight(actor, next.getCenter().x, next.getCenter().z,
                    referenceY + 0.05, MAX_STEP_DOWN + 0.05))) return false;
        }
        return true;
    }

    private static double supportHeight(LivingEntity actor, double x, double z, double ceiling, double depth) {
        double highest = Double.NEGATIVE_INFINITY;
        for (int y = Mth.floor(ceiling); y >= Mth.floor(ceiling - depth) - 1; y--) {
            BlockPos pos = new BlockPos(Mth.floor(x), y, Mth.floor(z));
            var state = actor.level().getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) continue;
            for (AABB shape : state.getCollisionShape(actor.level(), pos).toAabbs()) {
                double top = y + shape.maxY;
                if (x >= pos.getX() + shape.minX && x <= pos.getX() + shape.maxX
                        && z >= pos.getZ() + shape.minZ && z <= pos.getZ() + shape.maxZ
                        && top <= ceiling + 1.0e-6 && top >= ceiling - depth - 1.0e-6) highest = Math.max(highest, top);
            }
        }
        return highest;
    }
}
