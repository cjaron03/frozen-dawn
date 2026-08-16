package com.frozendawn.aggregate;

import java.util.ArrayList;
import java.util.List;

/** Pure tuning and selection policy for the Aggregate's finite expulsion waves. */
public final class AggregateDischargePolicy {
    public static final int WINDUP_TICKS = 96;
    public static final int CORE_EXPOSED_TICK = 44;
    public static final int EJECTION_TICK = 72;
    public static final int VULNERABILITY_TICKS = 80;
    public static final int CHILD_LIFETIME_TICKS = 1_800;
    public static final int PRIMARY_WAVE = 0;
    public static final int SECONDARY_WAVE = 1;

    private AggregateDischargePolicy() {
    }

    public static List<AggregateLineage> lineagesForWave(
            List<AggregateLineage> locked, boolean dominantUpgrade, int wave) {
        if (locked == null || locked.isEmpty()) return List.of();
        if (wave == PRIMARY_WAVE) return List.of(locked.getFirst());
        if (wave != SECONDARY_WAVE || locked.size() < 2) return List.of();

        List<AggregateLineage> result = new ArrayList<>(2);
        result.add(locked.get(1));
        if (!dominantUpgrade && locked.size() >= 3) result.add(locked.get(2));
        return List.copyOf(result);
    }

    public static float interruptThreshold(float maxHealth, int wave) {
        float fraction = wave == SECONDARY_WAVE ? 0.065F : 0.055F;
        return Math.max(24.0F, Math.max(1.0F, maxHealth) * fraction);
    }

    public static float exposedDamageMultiplier(int wave) {
        return wave == SECONDARY_WAVE ? 1.45F : 1.35F;
    }

    public static int substantialCap() {
        return 4;
    }

    public static int frostwritheFragmentCount(boolean dominantUpgrade) {
        return dominantUpgrade ? 5 : 4;
    }

    public static int substantialBodiesPerLineage() {
        return 2;
    }

    public static float massScaleForScars(int scars) {
        return switch (Math.clamp(scars, 0, 2)) {
            case 1 -> 0.90F;
            case 2 -> 0.79F;
            default -> 1.0F;
        };
    }
}
