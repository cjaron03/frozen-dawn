package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure eligibility and distance falloff rules for the Major Hearth storm.
 */
public final class HearthMasterArchitectWeatherPolicy {
    public static final int SYNC_INTERVAL_TICKS = 5;
    public static final double FULL_STRENGTH_RADIUS = 44.0D;
    public static final double OUTER_RADIUS = 112.0D;

    private HearthMasterArchitectWeatherPolicy() {
    }

    public static boolean canProject(
            ReturnedHearthSavedData.HearthRecord hearth,
            ReturnedHearthSavedData.HiveRelationship relationship,
            int phase,
            float progress) {
        return PhaseManager.isVacuumActive(phase, progress)
                && HearthMasterArchitectPolicy.isHostileRelationship(relationship)
                && HearthMasterArchitectPolicy.canHostMasterArchitect(hearth)
                && hearth.masterArchitectEntityId().isPresent()
                && !hearth.masterArchitectDefeated();
    }

    public static float strength(BlockPos center, Vec3 playerPosition) {
        double dx = playerPosition.x - (center.getX() + 0.5D);
        double dz = playerPosition.z - (center.getZ() + 0.5D);
        return strengthAtHorizontalDistance(Math.sqrt(dx * dx + dz * dz));
    }

    static float strengthAtHorizontalDistance(double distance) {
        if (distance <= FULL_STRENGTH_RADIUS) {
            return 1.0F;
        }
        if (distance >= OUTER_RADIUS) {
            return 0.0F;
        }

        float linear = (float) ((OUTER_RADIUS - distance)
                / (OUTER_RADIUS - FULL_STRENGTH_RADIUS));
        float clamped = Mth.clamp(linear, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
