package com.frozendawn.aggregate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.frozendawn.config.FrozenDawnConfig;

public final class StillpointPolicy {
    private StillpointPolicy() {
    }

    public static boolean isSuppressed(ServerLevel level, BlockPos pos) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        int radius = FrozenDawnConfig.STILLPOINT_RADIUS.get();
        return data.stillpointActive() && data.stillpointDimension()
                .filter(dimension -> dimension.equals(level.dimension().location()))
                .flatMap(dimension -> data.stillpointPos())
                .filter(anchor -> isWithinRadius(anchor, pos, radius))
                .isPresent();
    }

    public static boolean isWithinRadius(BlockPos anchor, BlockPos pos, int radius) {
        return anchor != null && pos != null && radius >= 0
                && anchor.distSqr(pos) <= (double) radius * radius;
    }

    public static double evolvedWeightMultiplier(ServerLevel level, BlockPos pos) {
        return isSuppressed(level, pos) ? 0.2D : 1.0D;
    }

    public static boolean segmentEnters(BlockPos anchor, net.minecraft.world.phys.Vec3 from,
                                        net.minecraft.world.phys.Vec3 to, double radius) {
        if (anchor == null || from == null || to == null || radius < 0.0D) return false;
        net.minecraft.world.phys.Vec3 center = anchor.getCenter();
        double radiusSqr = radius * radius;
        if (from.distanceToSqr(center) < radiusSqr) return false;
        net.minecraft.world.phys.Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-8D) return false;
        double t = net.minecraft.util.Mth.clamp(
                center.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return from.add(segment.scale(t)).distanceToSqr(center) < radiusSqr;
    }

    public static boolean chargeComplete(long chargeStart, long gameTime, int chargeTicks) {
        return chargeStart >= 0L && chargeTicks >= 0
                && gameTime - chargeStart >= chargeTicks;
    }

    public static net.minecraft.world.phys.Vec3 clampOutside(
            BlockPos anchor, net.minecraft.world.phys.Vec3 position, double radius) {
        net.minecraft.world.phys.Vec3 center = anchor.getCenter();
        net.minecraft.world.phys.Vec3 radial = position.subtract(center);
        if (radial.lengthSqr() < 1.0E-6D) radial = new net.minecraft.world.phys.Vec3(1, 0, 0);
        return center.add(radial.normalize().scale(radius + 0.35D));
    }
}
