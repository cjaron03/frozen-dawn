package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Bounded wave tuning for the dead drawn to the exposed Heart. */
public final class HeartScavengerPolicy {
    private HeartScavengerPolicy() {
    }

    public static Profile profile(int destroyedNodes, float fieldStrength) {
        int nodes = Mth.clamp(destroyedNodes, 0, HeartLattice.NODE_COUNT);
        float field = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        if (nodes >= HeartLattice.NODE_COUNT) {
            return new Profile(false, 0, 0, 0,
                    0.0F, 0.0F, 0.0F, 0.0F, false);
        }
        int baseSize = switch (nodes) {
            case 0, 1 -> 4;
            case 2 -> 5;
            case 3 -> 6;
            default -> 9;
        };
        int waveSize = Math.max(1, Math.round(baseSize * (0.86F + field * 0.22F)));
        int cap = switch (nodes) {
            case 0 -> 8;
            case 1 -> 10;
            case 2 -> 12;
            case 3 -> 16;
            default -> 22;
        };
        int interval = switch (nodes) {
            case 0 -> 180;
            case 1 -> 160;
            case 2 -> 140;
            case 3 -> 110;
            default -> 80;
        };
        float hollowChance = switch (nodes) {
            case 0 -> 0.28F;
            case 1 -> 0.32F;
            case 2 -> 0.36F;
            case 3 -> 0.20F;
            default -> 0.18F;
        };
        float returnedChance = nodes == 3 ? 0.25F : nodes >= 4 ? 0.30F : 0.0F;
        float mimicChance = nodes == 3 ? 0.11F : nodes >= 4 ? 0.15F : 0.0F;
        float architectChance = nodes == 3 ? 0.08F : nodes >= 4 ? 0.12F : 0.0F;
        return new Profile(true, interval, waveSize, cap,
                hollowChance, returnedChance, mimicChance, architectChance,
                nodes >= 3);
    }

    public static SpawnKind selectKind(Profile profile, float roll) {
        float value = Mth.clamp(roll, 0.0F, 0.999999F);
        float cursor = profile.architectChance();
        if (value < cursor) {
            return SpawnKind.ARCHITECT;
        }
        cursor += profile.mimicChance();
        if (value < cursor) {
            return SpawnKind.MIMIC;
        }
        cursor += profile.returnedChance();
        if (value < cursor) {
            return SpawnKind.RETURNED;
        }
        cursor += profile.hollowChance();
        return value < cursor ? SpawnKind.HOLLOW : SpawnKind.FROSTBITTEN;
    }

    public record Profile(
            boolean active,
            int intervalTicks,
            int waveSize,
            int concurrentCap,
            float hollowChance,
            float returnedChance,
            float mimicChance,
            float architectChance,
            boolean swarm) {
    }

    public enum SpawnKind {
        FROSTBITTEN,
        HOLLOW,
        RETURNED,
        MIMIC,
        ARCHITECT
    }
}
