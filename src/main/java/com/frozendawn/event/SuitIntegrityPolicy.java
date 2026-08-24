package com.frozendawn.event;

import com.frozendawn.item.O2TankItem;
import net.minecraft.util.Mth;

/** Pure tuning math for suit punctures, kept separate from event plumbing. */
public final class SuitIntegrityPolicy {

    public static final int EMERGENCY_REFILL_CAP_TICKS = 1200;
    public static final float EMERGENCY_REFILL_FRACTION = 0.35F;
    public static final float MAX_FALL_PUNCTURE_CHANCE = 0.30F;

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

    public static int emergencyRefillAmount(int totalCapacity) {
        if (totalCapacity <= 0) {
            return 0;
        }
        return Math.min(
                EMERGENCY_REFILL_CAP_TICKS,
                Math.max(1, (int) Math.ceil(totalCapacity * EMERGENCY_REFILL_FRACTION)));
    }

    public static boolean shouldConsumeBaselineO2(
            long gameTime, boolean visorRig, boolean efficiencyModule) {
        int interval = O2TankItem.BASE_CONSUMPTION_INTERVAL_TICKS
                * (visorRig ? 2 : 1);
        if (Math.floorMod(gameTime, interval) != 0) {
            return false;
        }
        long consumptionIndex = Math.floorDiv(gameTime, interval);
        return !efficiencyModule || Math.floorMod(consumptionIndex, 4L) != 0L;
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
                    MAX_FALL_PUNCTURE_CHANCE);
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
