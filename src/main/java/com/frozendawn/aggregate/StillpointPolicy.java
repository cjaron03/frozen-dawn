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
        return data.stillpointDimension()
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
}
