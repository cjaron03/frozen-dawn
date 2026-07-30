package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure lifecycle and mode policy for Orsathae-vaen. */
public final class HeartSuccessorPolicy {
    public static final int ASSEMBLY_TICKS = 100;
    public static final int RESPAWN_TICKS = 600;
    public static final int DEATH_TICKS = 60;
    public static final int STAGGER_TICKS = 180;
    public static final int CONDUCT_TICKS = 160;
    public static final int HEAL_TICKS = 120;

    private HeartSuccessorPolicy() {
    }

    public static boolean shouldExist(int destroyedMask) {
        int destroyed = HeartLattice.destroyedCount(destroyedMask);
        return destroyed >= 3 && destroyed < HeartLattice.NODE_COUNT;
    }

    public static int boundNode(int destroyedMask) {
        return shouldExist(destroyedMask) ? HeartLattice.nextNode(destroyedMask) : -1;
    }

    public static int generationForNode(int nodeIndex) {
        return Math.max(0, nodeIndex - 3);
    }

    public static float scale(int generation, float fieldStrength) {
        float field = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        return Mth.clamp((0.88F + field * 0.18F) - generation * 0.18F, 0.52F, 1.06F);
    }

    public static float staggerThreshold(int generation, float fieldStrength) {
        return 15.0F + Mth.clamp(fieldStrength, 0.0F, 1.0F) * 9.0F
                + generation * 3.0F;
    }

    public static float healPerSecond(int generation, float fieldStrength) {
        return Math.max(0.15F,
                (0.45F + Mth.clamp(fieldStrength, 0.0F, 1.0F) * 0.55F)
                        * (1.0F - generation * 0.22F));
    }

    public static Mode mode(long activeTicks) {
        long cycle = CONDUCT_TICKS + HEAL_TICKS;
        return Math.floorMod(activeTicks, cycle) < CONDUCT_TICKS
                ? Mode.CONDUCTING : Mode.HEALING;
    }

    public enum Mode {
        ASSEMBLING,
        CONDUCTING,
        HEALING,
        STAGGERED
    }
}
