package com.frozendawn.event;

import net.minecraft.util.Mth;

/** Pure tuning math for suit punctures, kept separate from event plumbing. */
public final class SuitIntegrityPolicy {

    public enum SourceKind {
        MASTER_ARCHITECT,
        ARCHITECT_HEAVY,
        MIMIC_AMBUSH,
        MIMIC_PHYSICAL,
        ORDINARY_PHYSICAL,
        FALL
    }

    private SuitIntegrityPolicy() {
    }

    public static float chance(
            SourceKind source,
            float fallDistance,
            float masterChance,
            float architectChance,
            float mimicAmbushChance,
            float ordinaryChance,
            float fallChancePerBlock) {
        return switch (source) {
            case MASTER_ARCHITECT -> clampChance(masterChance);
            case ARCHITECT_HEAVY -> clampChance(architectChance);
            case MIMIC_AMBUSH -> clampChance(mimicAmbushChance);
            case MIMIC_PHYSICAL, ORDINARY_PHYSICAL -> clampChance(ordinaryChance);
            case FALL -> Mth.clamp(
                    Math.max(0.0F, fallDistance) * Math.max(0.0F, fallChancePerBlock),
                    0.0F,
                    0.60F);
        };
    }

    public static double ventPerTick(int totalCapacity, int ventSeconds, int punctures) {
        if (totalCapacity <= 0 || punctures <= 0) {
            return 0.0D;
        }
        int durationTicks = Math.max(1, ventSeconds * 20);
        return totalCapacity / (double) durationTicks * punctures;
    }

    public static boolean canPuncture(int punctures, int graceTicks, int maxPunctures) {
        return graceTicks <= 0 && punctures < Math.max(1, maxPunctures);
    }

    private static float clampChance(float chance) {
        return Mth.clamp(chance, 0.0F, 1.0F);
    }
}
