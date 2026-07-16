package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;

/**
 * Eligibility, anchor, and peaceful-perimeter rules for the Major-Hearth apex.
 */
public final class HearthMasterArchitectPolicy {
    public static final long CHECK_INTERVAL_TICKS = 40L;
    public static final int HOME_RADIUS = 16;
    public static final int WATCH_DISTANCE = 36;
    public static final int RETREAT_DISTANCE = 8;
    public static final double DEFAULT_MAX_HEALTH = 300.0D;
    public static final double CINEMATIC_MAX_HEALTH = 200.0D;
    public static final double BRUTAL_MAX_HEALTH = 450.0D;
    public static final double ARMOR = 12.0D;
    public static final double KNOCKBACK_RESISTANCE = 1.0D;

    private static final long VARIANT_SALT = 0x4D41535445525F41L;

    private HearthMasterArchitectPolicy() {
    }

    public static boolean canHostMasterArchitect(
            ReturnedHearthSavedData.HearthRecord hearth) {
        return HearthPopulationPolicy.canHostPopulation(hearth);
    }

    public static BlockPos anchorOffset(long layoutSeed) {
        return IntactHearthLayout.masterArchitectAnchor(layoutSeed);
    }

    public static BlockPos anchor(ReturnedHearthSavedData.HearthRecord hearth) {
        return hearth.center().offset(anchorOffset(hearth.layoutSeed()));
    }

    public static int textureVariant(long layoutSeed) {
        return Math.floorMod((int) mix(layoutSeed ^ VARIANT_SALT), 5);
    }

    public static boolean isHostileRelationship(
            ReturnedHearthSavedData.HiveRelationship relationship) {
        return relationship == ReturnedHearthSavedData.HiveRelationship.ORSATHAE;
    }

    public static double maxHealthForPreset(String presetName) {
        if (presetName == null) {
            return DEFAULT_MAX_HEALTH;
        }
        return switch (presetName.toLowerCase(java.util.Locale.ROOT)) {
            case "brutal" -> BRUTAL_MAX_HEALTH;
            case "cinematic" -> CINEMATIC_MAX_HEALTH;
            default -> DEFAULT_MAX_HEALTH;
        };
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
