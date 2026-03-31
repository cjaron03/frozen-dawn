package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Shared helper for resolving the nearest deterministic ORSA field camp.
 */
public final class CampDirectiveHelper {

    private static final int CAMP_REGION_SIZE = 24;

    private CampDirectiveHelper() {
    }

    @Nullable
    public static CampDirective findNearestCamp(ServerLevel level, BlockPos origin) {
        long seed = level.getSeed();
        int originRegionX = Math.floorDiv(origin.getX() >> 4, CAMP_REGION_SIZE);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, CAMP_REGION_SIZE);
        CampDirective nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int drx = -6; drx <= 6; drx++) {
            for (int drz = -6; drz <= 6; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;
                int[] pos = CampPlacement.getCampBlockPos(seed, regionX, regionZ);
                if (pos == null || !CampPlacement.isEligibleCampSite(level, pos[0], pos[1])) {
                    continue;
                }

                BlockPos campPos = new BlockPos(pos[0], 0, pos[1]);
                double distSq = origin.distSqr(new BlockPos(pos[0], origin.getY(), pos[1]));
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = new CampDirective(campPos, formatCampDesignation(regionX, regionZ));
                }
            }
        }

        return nearest;
    }

    public static String formatCampDesignation(BlockPos campCenter) {
        int regionX = Math.floorDiv(campCenter.getX() >> 4, CAMP_REGION_SIZE);
        int regionZ = Math.floorDiv(campCenter.getZ() >> 4, CAMP_REGION_SIZE);
        return formatCampDesignation(regionX, regionZ);
    }

    public static String formatCampDesignation(int regionX, int regionZ) {
        char letter = (char) ('A' + Math.floorMod(regionX, 26));
        int number = Math.floorMod(regionZ, 99) + 1;
        return letter + "-" + String.format(Locale.US, "%02d", number);
    }

    public record CampDirective(BlockPos pos, String designation) {
    }
}
