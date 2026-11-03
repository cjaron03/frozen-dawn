package com.frozendawn.entity.blind;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Bounded escape routes using the remembered hazard and loaded local collision geometry. */
public final class BlindRetreatNavigation {
    private static final int MAX_PATH_QUERIES = 8;
    private static final double MIN_ESCAPE_PROGRESS = .05;
    private static final int[] DISTANCES = {8, 6, 4, 2, 1};
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2};
    private static final int[] ANGLES = {0, 45, -45, 90, -90};
    private BlindRetreatNavigation() { }

    public static Path find(ArchitectEntity actor, Vec3 origin) {
        Vec3 start = actor.position();
        Vec3 away = BlindArchitectPolicy.retreatGoal(start, origin, actor.getLookAngle()).subtract(start).normalize();
        double initialSeparation = horizontalDistance(start, origin);
        int queries = 0;
        for (int degrees : ANGLES) {
            double angle = Math.toRadians(degrees);
            Vec3 direction = new Vec3(away.x * Math.cos(angle) - away.z * Math.sin(angle), 0,
                    away.x * Math.sin(angle) + away.z * Math.cos(angle));
            for (int distance : DISTANCES) for (int height : HEIGHTS) {
                BlockPos goal = BlockPos.containing(start.add(direction.scale(distance)).add(0, height, 0));
                // Never feed rock to GroundPathNavigation: it shifts solid targets toward the surface.
                if (!standable(actor, goal)) continue;
                Vec3 endpoint = Vec3.atBottomCenterOf(goal);
                if (horizontalDistance(endpoint, origin) < initialSeparation + MIN_ESCAPE_PROGRESS) continue;
                if (++queries > MAX_PATH_QUERIES) return null;
                Path path = actor.getNavigation().createPath(goal, 0);
                // A mob straddling a hot block can get a start node on that block.
                // It already occupies that cell; avoid centering onto it before taking the escape step.
                if (path != null && path.getNodeCount() > 1 && path.getNextNodeIndex() == 0
                        && horizontalDistance(path.getEntityPosAtNode(actor, 0), origin) < initialSeparation - .1
                        && actor.getBoundingBox().intersects(new AABB(path.getNodePos(0)))) path.advance();
                if (safe(actor, path, origin, MIN_ESCAPE_PROGRESS)) return path;
            }
        }
        return null;
    }

    private static boolean standable(ArchitectEntity actor, BlockPos feet) {
        var level = actor.level();
        return level.hasChunkAt(feet) && level.hasChunkAt(feet.above()) && level.hasChunkAt(feet.below())
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    public static boolean isEscape(ArchitectEntity actor, Path path, Vec3 origin) {
        return safe(actor, path, origin, 0);
    }

    private static boolean safe(ArchitectEntity actor, Path path, Vec3 origin, double progress) {
        double separation = horizontalDistance(actor.position(), origin);
        if (path == null || path.getNodeCount() == 0) return false;
        // A partial route is useful only if its actual nodes retreat; the requested goal is insufficient.
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++)
            if (horizontalDistance(path.getEntityPosAtNode(actor, i), origin) < separation - .1) return false;
        return horizontalDistance(path.getEntityPosAtNode(actor, path.getNodeCount() - 1), origin) >= separation + progress;
    }

    private static double horizontalDistance(Vec3 point, Vec3 origin) {
        return Math.hypot(point.x - origin.x, point.z - origin.z);
    }
}
