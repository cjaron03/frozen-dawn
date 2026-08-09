package com.frozendawn.hearthrot;

import net.minecraft.util.Mth;

/** Pure tuning and threshold policy for Hearthrot. */
public final class HearthrotPolicy {
    public static final int MAX_COLONIZATION = 10_000;
    public static final int FIRST_VISIBLE_COLONIZATION = 2_500;
    public static final int SALVATION_STILLNESS_TICKS = 25 * 20;
    public static final int[] VISIBLE_THRESHOLDS = {2_500, 5_000, 7_500, 10_000};
    private static final int[] NORMAL_STAGE_TICKS = {
            0, 100 * 60 * 20, 80 * 60 * 20, 65 * 60 * 20,
            60 * 60 * 20, 55 * 60 * 20
    };

    private HearthrotPolicy() {
    }

    public enum Preset {
        NORMAL(1.0D), CINEMATIC(1.5D), BRUTAL(0.75D);

        private final double durationScale;

        Preset(double durationScale) {
            this.durationScale = durationScale;
        }
    }

    public static int visualStage(int colonization) {
        int clamped = Mth.clamp(colonization, 0, MAX_COLONIZATION);
        for (int index = VISIBLE_THRESHOLDS.length - 1; index >= 0; index--) {
            if (clamped >= VISIBLE_THRESHOLDS[index]) {
                return index + 1;
            }
        }
        return 0;
    }

    public static boolean isInfectable(int colonization) {
        return colonization >= FIRST_VISIBLE_COLONIZATION;
    }

    public static double coreColonizationPerTick() {
        return MAX_COLONIZATION / (120.0D * 60.0D * 20.0D);
    }

    public static double exposureMultiplier(int bandOrdinal) {
        return switch (Mth.clamp(bandOrdinal, 0, 2)) {
            case 2 -> 1.0D;
            case 1 -> 0.45D;
            default -> 0.20D;
        };
    }

    public static double activeHeatCleaningPerTick() {
        return MAX_COLONIZATION / (20.0D * 60.0D * 20.0D);
    }

    public static double warmInteriorCleaningPerTick() {
        return MAX_COLONIZATION / (40.0D * 60.0D * 20.0D);
    }

    public static int stageDurationTicks(int stage, Preset preset) {
        if (stage <= 0 || stage >= 6) {
            return 0;
        }
        return Math.max(1, (int) Math.round(NORMAL_STAGE_TICKS[stage]
                * preset.durationScale));
    }

    public static double progressionRate(
            int bandOrdinal,
            float displayedTemperature,
            boolean moving,
            int colonization) {
        double bloom = switch (bandOrdinal) {
            case 2 -> 1.0D;
            case 1 -> 0.72D;
            case 0 -> 0.52D;
            default -> 0.32D;
        };
        double warmth = displayedTemperature >= 15.0F ? 0.18D
                : displayedTemperature >= 0.0F ? 0.42D
                : displayedTemperature <= -90.0F ? 1.15D : 1.0D;
        double motion = moving ? 0.55D : 1.0D;
        double rig = 1.0D + 0.25D * Mth.clamp(
                colonization / (double) MAX_COLONIZATION, 0.0D, 1.0D);
        return Mth.clamp(bloom * warmth * motion * rig, 0.03D, 2.5D);
    }

    public static double externalO2Multiplier(int visualStage) {
        return switch (Mth.clamp(visualStage, 0, 4)) {
            case 1 -> 1.08D;
            case 2 -> 1.16D;
            case 3 -> 1.24D;
            case 4 -> 1.32D;
            default -> 1.0D;
        };
    }

    public static double temporarySealLifetimeMultiplier(int visualStage) {
        return switch (Mth.clamp(visualStage, 0, 4)) {
            case 1 -> 0.90D;
            case 2 -> 0.80D;
            case 3 -> 0.65D;
            case 4 -> 0.50D;
            default -> 1.0D;
        };
    }

    public static double diseaseO2Multiplier(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 1.15D;
            case 5 -> 1.30D;
            case 6 -> 1.50D;
            default -> 1.0D;
        };
    }

    public static float hiddenColdPenalty(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 15.0F;
            case 5 -> 25.0F;
            case 6 -> 40.0F;
            default -> 0.0F;
        };
    }

    public static double movementPenalty(int stage, boolean suited) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> suited ? -0.20D : -0.10D;
            case 5 -> suited ? -0.25D : -0.15D;
            case 6 -> suited ? -0.35D : -0.20D;
            default -> 0.0D;
        };
    }

    public static int foodFreezeMultiplier(int stage) {
        return stage >= 6 ? 3 : stage >= 5 ? 2 : 1;
    }

    public static int maxHealthPenaltyHearts(int stage) {
        return Math.max(0, Math.min(5, stage - 1));
    }

    public static boolean usesCrystallineHurtSounds(int stage) {
        return stage >= 3;
    }

    public static int stageAfterDeath(int stage) {
        return stage <= 0 ? 0 : Math.max(1, stage - 1);
    }

    public static boolean shouldRollSalvation(
            int stationaryTicks, boolean alreadyRolled) {
        return stationaryTicks >= SALVATION_STILLNESS_TICKS && !alreadyRolled;
    }

    public static int coughMinimumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 3 -> 75;
            case 4 -> 65;
            case 5 -> 55;
            case 6 -> 45;
            default -> 0;
        };
    }

    public static int coughMaximumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 3 -> 150;
            case 4 -> 130;
            case 5 -> 110;
            case 6 -> 90;
            default -> 0;
        };
    }

    public static int wheezeMinimumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 120;
            case 5 -> 95;
            case 6 -> 70;
            default -> 0;
        };
    }

    public static int wheezeMaximumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 260;
            case 5 -> 220;
            case 6 -> 180;
            default -> 0;
        };
    }

    public static int breathCatchMinimumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 160;
            case 5 -> 120;
            case 6 -> 90;
            default -> 0;
        };
    }

    public static int breathCatchMaximumSeconds(int stage) {
        return switch (Mth.clamp(stage, 0, 6)) {
            case 4 -> 300;
            case 5 -> 240;
            case 6 -> 180;
            default -> 0;
        };
    }
}
