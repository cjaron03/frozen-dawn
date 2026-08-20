package com.frozendawn.aggregate;

import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.util.Mth;

public final class AggregateCombatPolicy {
    public static final float PHASE_TWO_AT = 0.70F;
    public static final float PHASE_THREE_AT = 0.35F;
    public static final double OVERFEED_FULL_SCALE = 400.0D;

    private AggregateCombatPolicy() {
    }

    public static float baseHealth(ConfigPresets preset) {
        if (preset == ConfigPresets.CINEMATIC) {
            return FrozenDawnConfig.AGGREGATE_CINEMATIC_HEALTH.get();
        }
        if (preset == ConfigPresets.BRUTAL) {
            return FrozenDawnConfig.AGGREGATE_BRUTAL_HEALTH.get();
        }
        return FrozenDawnConfig.AGGREGATE_NORMAL_HEALTH.get();
    }

    public static float overfedCap(ConfigPresets preset) {
        if (preset == ConfigPresets.CINEMATIC) {
            return FrozenDawnConfig.AGGREGATE_CINEMATIC_OVERFED_CAP.get();
        }
        if (preset == ConfigPresets.BRUTAL) {
            return FrozenDawnConfig.AGGREGATE_BRUTAL_OVERFED_CAP.get();
        }
        return FrozenDawnConfig.AGGREGATE_NORMAL_OVERFED_CAP.get();
    }

    public static float awakenedHealth(ConfigPresets preset, double overfeedPressure) {
        float fraction = (float) Mth.clamp(
                overfeedPressure / OVERFEED_FULL_SCALE, 0.0D, 1.0D);
        return Mth.lerp(fraction, baseHealth(preset), overfedCap(preset));
    }

    public static double effectiveOverfeed(
            double overfeedPressure, AggregateLineage dominant) {
        return Math.max(0.0D, overfeedPressure)
                * (dominant == AggregateLineage.UNDONE ? 1.25D : 1.0D);
    }

    public static float participantMultiplier(int participants) {
        return switch (Math.max(1, participants)) {
            case 1 -> 1.0F;
            case 2 -> FrozenDawnConfig.AGGREGATE_TWO_PLAYER_MULTIPLIER.get().floatValue();
            case 3 -> FrozenDawnConfig.AGGREGATE_THREE_PLAYER_MULTIPLIER.get().floatValue();
            case 4 -> FrozenDawnConfig.AGGREGATE_FOUR_PLAYER_MULTIPLIER.get().floatValue();
            default -> FrozenDawnConfig.AGGREGATE_FIVE_PLAYER_MULTIPLIER.get().floatValue();
        };
    }

    public static AggregatePhase phaseForFraction(float healthFraction) {
        if (healthFraction <= PHASE_THREE_AT) return AggregatePhase.CONVERGENCE_FAILURE;
        if (healthFraction <= PHASE_TWO_AT) return AggregatePhase.REALLOCATED;
        return AggregatePhase.COHERENT;
    }

    public static int activeTraitCount(
            AggregatePhase phase, int availableTraits, boolean dominant) {
        int available = Math.max(0, availableTraits);
        if (phase == AggregatePhase.COHERENT) return Math.min(1, available);
        if (phase == AggregatePhase.REALLOCATED) return Math.min(2, available);
        if (phase == AggregatePhase.CONVERGENCE_FAILURE) {
            return Math.min(dominant ? 2 : 3, available);
        }
        return 0;
    }

    public static float fragmentReturnHeal(
            float maxHealth, float alreadyHealed, AggregatePhase phase) {
        if (maxHealth <= 0.0F || phase == AggregatePhase.CONVERGENCE_FAILURE
                || phase == AggregatePhase.DYING || phase == AggregatePhase.DEAD) {
            return 0.0F;
        }
        float cap = maxHealth * 0.10F;
        return Math.min(maxHealth * 0.02F,
                Math.max(0.0F, cap - Math.max(0.0F, alreadyHealed)));
    }
}
