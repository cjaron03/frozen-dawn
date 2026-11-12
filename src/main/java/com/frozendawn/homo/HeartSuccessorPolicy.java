package com.frozendawn.homo;

import net.minecraft.util.Mth;

import java.util.List;

/** Pure lifecycle and mode policy for Orsathae-vaen. */
public final class HeartSuccessorPolicy {
    public static final int ASSEMBLY_TICKS = 100;
    public static final int RESPAWN_TICKS = 600;
    public static final int DEATH_TICKS = 60;
    public static final int STAGGER_TICKS = 180;
    public static final int CONDUCT_TICKS = 160;
    public static final int HEAL_TICKS = 120;
    public static final int MAX_SUPPORT_LINKS = 3;
    public static final float EMERGENCY_HEAL_THRESHOLD = 0.35F;
    public static final float HEAL_RELEASE_THRESHOLD = 0.72F;
    public static final double SUPPORT_SPEED_BONUS = 0.24D;
    public static final double SUPPORT_DAMAGE_BONUS = 0.30D;
    public static final float SUPPORT_DAMAGE_TAKEN_MULTIPLIER = 0.75F;
    /** The random voice lines, as sound ids under entity.heart_successor; Kevin is not one. */
    public static final List<String> BARKS =
            List.of("voice_why", "voice_cold", "voice_hello", "voice_dont");
    public static final int KEVIN_DELAY_TICKS = 30;

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

    public static boolean needsEmergencyHealing(float health, float maxHealth) {
        return maxHealth > 0.0F && health / maxHealth <= EMERGENCY_HEAL_THRESHOLD;
    }

    public static boolean shouldContinueHealing(float health, float maxHealth) {
        return maxHealth > 0.0F && health / maxHealth < HEAL_RELEASE_THRESHOLD;
    }

    public static float mitigateSupportedDamage(float damage) {
        return Math.max(0.0F, damage) * SUPPORT_DAMAGE_TAKEN_MULTIPLIER;
    }

    /**
     * CH-29: each Successor says Kevin once, 30 ticks into its first stagger, or into its
     * death if it never staggers. An elapsed value of -1 means that has not started.
     */
    public static boolean kevinDue(boolean spoken, int staggerElapsed, int deathElapsed) {
        return !spoken && (staggerElapsed >= KEVIN_DELAY_TICKS
                || deathElapsed >= KEVIN_DELAY_TICKS);
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
