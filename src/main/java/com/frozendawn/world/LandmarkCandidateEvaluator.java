package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Shared worldgen-time landmark evaluation helpers.
 * Stage-one landmark planning stays intentionally cheap and center-only.
 */
public final class LandmarkCandidateEvaluator {

    private LandmarkCandidateEvaluator() {
    }

    static ExactTargetCandidate evaluateBlastPitCandidate(ServerLevel overworld, int centerX, int centerZ,
                                                          int distance, int targetDistance) {
        int centerY = worldgenSurfaceY(overworld, centerX, centerZ);
        if (centerY <= overworld.getMinBuildHeight() + 1 || !isEligibleLandmarkCenterBiome(overworld, centerX, centerZ)) {
            return null;
        }

        return new ExactTargetCandidate(new BlockPos(centerX, centerY, centerZ), 0,
                Math.abs(distance - targetDistance), 0);
    }

    static ExactTargetCandidate evaluateTowerCandidate(ServerLevel overworld, int centerX, int centerZ, int distance,
                                                       double angle, double sectorAngle, int targetDistance) {
        int centerY = worldgenSurfaceY(overworld, centerX, centerZ);
        if (centerY <= overworld.getMinBuildHeight() + 1 || !isEligibleLandmarkCenterBiome(overworld, centerX, centerZ)) {
            return null;
        }

        return new ExactTargetCandidate(new BlockPos(centerX, centerY, centerZ), 0,
                Math.abs(distance - targetDistance),
                (int) Math.round(Math.abs(angle - sectorAngle) * 1000.0D));
    }

    static boolean isEligibleLandmarkCenterBiome(ServerLevel overworld, int x, int z) {
        return LandmarkBiomeRules.isEligibleLandmarkBiome(overworld, x, z);
    }

    static boolean isToleratedLandmarkFootprintBiome(ServerLevel overworld, int x, int z) {
        return LandmarkBiomeRules.isToleratedLandmarkFootprintBiome(overworld, x, z);
    }

    static int worldgenSurfaceY(ServerLevel overworld, int x, int z) {
        var chunkSource = overworld.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, overworld, chunkSource.randomState());
    }

    static boolean hasWorldgenWater(ServerLevel overworld, int x, int z) {
        var chunkSource = overworld.getChunkSource();
        int surfaceY = chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, overworld, chunkSource.randomState());
        int oceanFloorY = chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.OCEAN_FLOOR_WG, overworld, chunkSource.randomState());
        return surfaceY != oceanFloorY;
    }

    static void addTopCandidate(List<ExactTargetCandidate> candidates, ExactTargetCandidate candidate, int limit) {
        candidates.add(candidate);
        candidates.sort(ExactTargetCandidate::compareTo);
        if (candidates.size() > limit) {
            candidates.remove(candidates.size() - 1);
        }
    }

    static long flatDistanceSq(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    static record ExactTargetCandidate(BlockPos pos, int heightVariation, int distancePenalty, int anglePenalty)
            implements Comparable<ExactTargetCandidate> {

        @Override
        public int compareTo(ExactTargetCandidate other) {
            int variationOrder = Integer.compare(this.heightVariation, other.heightVariation);
            if (variationOrder != 0) {
                return variationOrder;
            }
            int distanceOrder = Integer.compare(this.distancePenalty, other.distancePenalty);
            if (distanceOrder != 0) {
                return distanceOrder;
            }
            int angleOrder = Integer.compare(this.anglePenalty, other.anglePenalty);
            if (angleOrder != 0) {
                return angleOrder;
            }
            int xOrder = Integer.compare(this.pos.getX(), other.pos.getX());
            if (xOrder != 0) {
                return xOrder;
            }
            return Integer.compare(this.pos.getZ(), other.pos.getZ());
        }
    }
}
