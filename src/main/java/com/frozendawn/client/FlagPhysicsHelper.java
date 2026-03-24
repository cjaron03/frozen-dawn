package com.frozendawn.client;

import com.frozendawn.phase.FrozenDawnPhaseTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Client-side cloth simulation for the ORSA flag.
 * 8-segment chain with phase-responsive stiffness, damping, and wind.
 */
public final class FlagPhysicsHelper {

    public static final int SEGMENTS = 8;

    public record FlagPhysicsTuning(
            float stiffness,
            float damping,
            float maxBendDeg,
            float baseLeanDeg,
            float flutterDeg,
            float flutterSpeed,
            float tipInfluence,
            float followFactor,
            float impulseFlutterDeg
    ) {}

    private FlagPhysicsHelper() {}

    public static FlagPhysicsTuning getTuning(int phase) {
        return switch (phase) {
            case 0, 1 -> new FlagPhysicsTuning(0.16f, 0.80f, 16.0f, 9.5f, 5.5f, 0.18f, 1.00f, 0.82f, 4.0f);
            case 2    -> new FlagPhysicsTuning(0.18f, 0.82f, 13.0f, 8.0f, 4.0f, 0.16f, 0.90f, 0.85f, 3.0f);
            case 3    -> new FlagPhysicsTuning(0.22f, 0.85f, 9.0f, 5.8f, 2.8f, 0.14f, 0.75f, 0.89f, 2.0f);
            case 4    -> new FlagPhysicsTuning(0.26f, 0.88f, 5.0f, 3.0f, 1.4f, 0.12f, 0.45f, 0.93f, 1.0f);
            case 5    -> new FlagPhysicsTuning(0.30f, 0.90f, 2.0f, 1.1f, 0.35f, 0.10f, 0.18f, 0.96f, 0.45f);
            default   -> new FlagPhysicsTuning(0.35f, 0.92f, 0.75f, 0.0f, 0.0f, 0.0f, 0.0f, 0.98f, 0.75f); // phase 6: vacuum
        };
    }

    public static float computeWindStrength(BlockPos pos, int phase, long gameTime, float impulseStrength) {
        if (phase >= 6) {
            return impulseStrength; // vacuum — only impulse moves the flag
        }

        float base = switch (phase) {
            case 0    -> 0.25f;
            case 1    -> 0.35f;
            case 2    -> 0.50f;
            case 3    -> 0.70f;
            case 4    -> 0.85f;
            case 5    -> 1.00f;
            default   -> 0.0f;
        };

        float oscillation = 0.15f * (float) Math.sin(gameTime * 0.05 + pos.getX() * 0.13 + pos.getZ() * 0.11);
        return Math.max(0.0f, base + oscillation + impulseStrength);
    }

    public static float getTipSag(int phase) {
        return switch (phase) {
            case 0, 1 -> 1.75f / 16.0f;
            case 2    -> 1.5f / 16.0f;
            case 3    -> 1.0f / 16.0f;
            case 4    -> 0.65f / 16.0f;
            case 5    -> 0.35f / 16.0f;
            default   -> 0.0f;
        };
    }

    /**
     * Steps the cloth simulation forward one tick.
     *
     * @param angles            per-segment bend angles (mutated in place)
     * @param angularVelocities per-segment angular velocities (mutated in place)
     * @param pos               block position (used for wind variation)
     * @param gameTime          level game time
     * @param impulseStrength   current transient impulse value
     * @return decayed impulse strength for next tick
     */
    public static float tickSimulation(float[] angles, float[] angularVelocities,
                                       BlockPos pos, long gameTime, float impulseStrength) {
        int phase = FrozenDawnPhaseTracker.getPhase();
        FlagPhysicsTuning tuning = getTuning(phase);
        float wind = computeWindStrength(pos, phase, gameTime, impulseStrength);
        float time = gameTime + (pos.getX() * 0.17f) + (pos.getZ() * 0.11f);

        for (int i = 0; i < SEGMENTS; i++) {
            float previousAngle = (i == 0) ? 0.0f : angles[i - 1];
            float t = i / (float) (SEGMENTS - 1);
            float tipFactor = 0.20f + t * tuning.tipInfluence();

            // Flags usually hold a prevailing lean away from the pole, then flutter around that baseline.
            float leanTarget = wind * tuning.baseLeanDeg() * tipFactor;
            float flutterWave = Mth.sin(time * tuning.flutterSpeed() - i * 0.85f);
            float flutterTarget = flutterWave * tuning.flutterDeg() * wind * tipFactor;
            float impulseWave = Mth.sin(time * 0.33f - i * 1.15f + 0.7f);
            float impulseTarget = impulseStrength * tuning.impulseFlutterDeg() * tipFactor * impulseWave;
            float targetAngle = leanTarget + flutterTarget + impulseTarget;
            targetAngle = Mth.lerp(1.0f - tuning.followFactor(), previousAngle, targetAngle);
            targetAngle = Mth.clamp(targetAngle, -tuning.maxBendDeg(), tuning.maxBendDeg());

            float accel = (targetAngle - angles[i]) * tuning.stiffness();
            angularVelocities[i] += accel;
            angularVelocities[i] *= tuning.damping();
            angles[i] += angularVelocities[i];
            angles[i] = Mth.clamp(angles[i], -tuning.maxBendDeg(), tuning.maxBendDeg());
        }

        // Decay impulse
        impulseStrength *= 0.90f;
        if (impulseStrength < 0.01f) impulseStrength = 0.0f;
        return impulseStrength;
    }
}
