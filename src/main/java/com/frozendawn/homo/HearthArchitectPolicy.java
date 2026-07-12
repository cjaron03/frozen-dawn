package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure eligibility, timing, and geometry rules for the Major-Hearth assessor.
 */
public final class HearthArchitectPolicy {
    public static final String PROFILE = "architect_assessor";
    public static final int MIN_SPAWN_RADIUS = 10;
    public static final int MAX_SPAWN_RADIUS = 16;
    public static final int SPAWN_ATTEMPTS = 24;
    public static final int HOME_RADIUS = 24;
    public static final int WATCH_DISTANCE = 32;
    public static final int ASSESSMENT_MIN_DISTANCE = 10;
    public static final int ASSESSMENT_MAX_DISTANCE = 24;
    public static final int ASSESSMENT_TICKS = 100;

    private static final long SPAWN_SALT = 0x4153534553534F52L;
    private static final long VARIANT_SALT = 0x415243485F564152L;

    private HearthArchitectPolicy() {
    }

    public static boolean canHostAssessor(ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.type() == HearthSelectionPolicy.HearthType.MAJOR
                && hearth.stage() == ReturnedHearthSavedData.HearthStage.INTACT
                && hearth.surfaceResolved()
                && hearth.structurePlaced();
    }

    public static List<BlockPos> spawnOffsets(long layoutSeed) {
        RandomSource random = RandomSource.create(mix(layoutSeed ^ SPAWN_SALT));
        double baseAngle = random.nextDouble() * Math.PI * 2.0D;
        List<BlockPos> offsets = new ArrayList<>(SPAWN_ATTEMPTS);
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = baseAngle + attempt * (Math.PI * 2.0D / SPAWN_ATTEMPTS);
            int radius = MIN_SPAWN_RADIUS
                    + random.nextInt(MAX_SPAWN_RADIUS - MIN_SPAWN_RADIUS + 1);
            offsets.add(new BlockPos(
                    (int) Math.round(Math.cos(angle) * radius),
                    0,
                    (int) Math.round(Math.sin(angle) * radius)));
        }
        return List.copyOf(offsets);
    }

    public static int textureVariant(long layoutSeed) {
        return Math.floorMod((int) mix(layoutSeed ^ VARIANT_SALT), 5);
    }

    public static boolean isAssessmentDistance(double distanceSquared) {
        double minimum = (double) ASSESSMENT_MIN_DISTANCE * ASSESSMENT_MIN_DISTANCE;
        double maximum = (double) ASSESSMENT_MAX_DISTANCE * ASSESSMENT_MAX_DISTANCE;
        return distanceSquared >= minimum && distanceSquared <= maximum;
    }

    public static boolean shouldReturnHome(double distanceSquared) {
        return distanceSquared > (double) HOME_RADIUS * HOME_RADIUS;
    }

    public static ReturnedHearthSavedData.HiveRelationship relationshipAfterAssessment(
            ReturnedHearthSavedData.HiveRelationship current, boolean hasOrsaTechnology) {
        if (current == ReturnedHearthSavedData.HiveRelationship.ORSATHAE) {
            return current;
        }
        return hasOrsaTechnology
                ? ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS
                : current;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
