package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Keeps surface destinations above growing drifts without snapping to roofs or other floors. */
final class ArchitectWildernessWaypoint {
    private ArchitectWildernessWaypoint() {}

    static Vec3 surface(ServerLevel level, LivingEntity target, Vec3 original) {
        BlockPos column = BlockPos.containing(original);
        // Production snowfall stacks at most three blocks. Search locally, not
        // from the heightmap, which could select a canopy or an unrelated roof.
        for (int y = column.getY() - 1; y <= column.getY() + 2; y++) {
            BlockPos support = new BlockPos(column.getX(), y, column.getZ());
            var shape = level.getBlockState(support).getCollisionShape(level, support);
            if (shape.isEmpty()) continue;
            double feet = y + shape.max(Direction.Axis.Y);
            if (feet < original.y || feet > original.y + 3) continue;
            Vec3 candidate = new Vec3(original.x, feet, original.z);
            var bounds = target.getBoundingBox().move(candidate.subtract(target.position()));
            if (level.noCollision(target, bounds.deflate(1.0E-5))) return candidate;
        }
        return original; // A blocked destination stays unresolved, never silently skipped.
    }

    static boolean arrived(Vec3 position, Vec3 surfaceGoal) {
        return ArchitectWildernessProgress.horizontalDistance(position, surfaceGoal) < 1.5
                && Math.abs(position.y - surfaceGoal.y) <= 1.25;
    }
}
