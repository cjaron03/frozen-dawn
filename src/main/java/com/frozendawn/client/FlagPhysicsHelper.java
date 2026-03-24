package com.frozendawn.client;

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
            float impulseFlutterDeg,
            float rippleDeg,
            float rippleSpeed,
            float snapDeg
    ) {}

    private FlagPhysicsHelper() {}

    public static FlagPhysicsTuning getTuning(int phase, float progress) {
        return switch (phase) {
            case 0, 1 -> new FlagPhysicsTuning(0.19f, 0.81f, 20.0f, 13.5f, 8.5f, 0.22f, 1.12f, 0.79f, 5.5f, 5.0f, 0.42f, 1.5f);
            case 2    -> new FlagPhysicsTuning(0.21f, 0.83f, 18.0f, 11.2f, 7.2f, 0.20f, 1.02f, 0.82f, 4.5f, 4.4f, 0.39f, 1.2f);
            case 3    -> new FlagPhysicsTuning(0.24f, 0.85f, 15.0f, 9.0f, 5.8f, 0.19f, 0.90f, 0.85f, 3.6f, 3.6f, 0.36f, 1.0f);
            case 4    -> new FlagPhysicsTuning(0.29f, 0.88f, 12.0f, 9.5f, 6.8f, 0.23f, 0.78f, 0.88f, 2.3f, 5.4f, 0.56f, 3.2f);
            case 5    -> new FlagPhysicsTuning(0.39f, 0.88f, 16.0f, 18.0f, 13.0f, 0.36f, 0.98f, 0.84f, 3.0f, 12.5f, 0.86f, 9.5f);
            default   -> {
                if (progress <= BlizzardWindHelper.PHASE6_FULL_BLIZZARD_END) {
                    yield new FlagPhysicsTuning(0.43f, 0.86f, 20.0f, 21.0f, 15.5f, 0.42f, 1.10f, 0.80f, 3.8f, 15.0f, 1.02f, 12.5f);
                }
                if (progress < BlizzardWindHelper.PHASE6_WIND_END) {
                    yield new FlagPhysicsTuning(0.42f, 0.93f, 3.0f, 3.0f, 1.6f, 0.18f, 0.24f, 0.96f, 0.6f, 1.2f, 0.34f, 0.9f);
                }
                yield new FlagPhysicsTuning(0.54f, 0.96f, 0.45f, 0.0f, 0.0f, 0.0f, 0.0f, 0.996f, 0.18f, 0.0f, 0.0f, 0.0f);
            }
        };
    }

    public static float computeWindStrength(BlockPos pos, int phase, float progress, long gameTime, float impulseStrength) {
        if (phase >= 6 && progress >= BlizzardWindHelper.PHASE6_WIND_END) {
            return impulseStrength * 0.35f; // late vacuum — only residual impulse moves the flag
        }

        if (phase >= 5) {
            float stormStrength = BlizzardWindHelper.getNormalizedSurfaceWindStrength(phase, progress, gameTime);
            boolean phase6Early = phase >= 6 && progress <= BlizzardWindHelper.PHASE6_FULL_BLIZZARD_END;
            float gustScale = phase6Early ? 0.38f : phase == 5 ? 0.30f : 0.16f;
            float gust = gustScale * Mth.sin(gameTime * 0.13f + pos.getX() * 0.09f + pos.getZ() * 0.07f);
            float base = phase6Early
                    ? 1.18f + stormStrength * 0.96f
                    : phase == 5
                    ? 1.00f + stormStrength * 0.72f
                    : stormStrength;
            float impulseScale = phase6Early ? 0.96f : phase == 5 ? 0.82f : 0.60f;
            float maxWind = phase6Early ? 2.30f : phase == 5 ? 1.85f : 1.35f;
            return Mth.clamp(base + gust + impulseStrength * impulseScale, 0.0f, maxWind);
        }

        float base = switch (phase) {
            case 0    -> 0.36f;
            case 1    -> 0.50f;
            case 2    -> 0.66f;
            case 3    -> 0.82f;
            case 4    -> 0.98f;
            default   -> 0.0f;
        };

        float oscillation = 0.16f * Mth.sin(gameTime * 0.06f + pos.getX() * 0.13f + pos.getZ() * 0.11f);
        float gust = 0.10f * Mth.sin(gameTime * 0.12f + pos.getX() * 0.05f - pos.getZ() * 0.09f);
        return Math.max(0.0f, base + oscillation + gust + impulseStrength * 0.80f);
    }

    public static float getTipSag(int phase) {
        return switch (phase) {
            case 0, 1 -> 1.1f / 16.0f;
            case 2    -> 0.9f / 16.0f;
            case 3    -> 0.65f / 16.0f;
            case 4    -> 0.35f / 16.0f;
            case 5    -> 0.18f / 16.0f;
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
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        FlagPhysicsTuning tuning = getTuning(phase, progress);
        float wind = computeWindStrength(pos, phase, progress, gameTime, impulseStrength);
        float time = gameTime + (pos.getX() * 0.17f) + (pos.getZ() * 0.11f);
        boolean phase6Early = phase >= 6 && progress <= BlizzardWindHelper.PHASE6_FULL_BLIZZARD_END;
        boolean stormChaos = phase == 5 || phase6Early;
        float gustPulse = 0.5f + 0.5f * Mth.sin(time * (phase6Early ? 0.16f : phase == 5 ? 0.12f : 0.08f) + pos.getX() * 0.03f - pos.getZ() * 0.04f);
        float stormPulse = 0.5f + 0.5f * Mth.sin(time * (phase6Early ? 0.33f : phase == 5 ? 0.26f : 0.17f) + pos.getX() * 0.11f + pos.getZ() * 0.09f);

        for (int i = 0; i < SEGMENTS; i++) {
            float previousAngle = (i == 0) ? 0.0f : angles[i - 1];
            float t = i / (float) (SEGMENTS - 1);
            float tipFactor = 0.20f + t * tuning.tipInfluence();
            float freeEdgeFactor = t * t;
            float phaseViolence = phase6Early ? 1.80f : phase == 5 ? 1.45f : 1.0f;

            // Flags usually hold a prevailing lean away from the pole, then flutter around that baseline.
            float leanTarget = wind * tuning.baseLeanDeg() * tipFactor * (stormChaos ? phase6Early ? 1.18f : 1.12f : 1.0f);
            float flutterWave = Mth.sin(time * tuning.flutterSpeed() - i * 0.85f);
            float flutterTarget = flutterWave * tuning.flutterDeg() * wind * (0.30f + tipFactor * 0.70f) * phaseViolence;
            float rippleWave = Mth.sin(time * tuning.rippleSpeed() - i * 1.50f + gustPulse * 1.2f);
            float rippleTarget = rippleWave * tuning.rippleDeg() * wind * (0.12f + freeEdgeFactor * 0.88f) * (0.55f + gustPulse * 0.45f) * phaseViolence;
            float snapWave = Mth.sin(time * (tuning.rippleSpeed() * (phase6Early ? 2.40f : phase == 5 ? 2.10f : 1.70f)) - i * 2.20f + 0.8f);
            float snapTarget = snapWave * tuning.snapDeg() * wind * freeEdgeFactor
                    * (phase >= 4 ? 0.55f + stormPulse * 0.45f : 0.25f + gustPulse * 0.35f)
                    * phaseViolence;
            float whipTarget = 0.0f;
            if (stormChaos) {
                float whipWave = Mth.sin(time * (phase6Early ? 2.30f : 1.95f) - i * 2.9f + stormPulse * 2.2f);
                float whipScale = phase6Early ? 1.25f : 1.0f;
                whipTarget = whipWave * tuning.snapDeg() * wind * freeEdgeFactor * (0.45f + stormPulse * 0.55f) * whipScale;
            }
            float impulseWave = Mth.sin(time * 0.33f - i * 1.15f + 0.7f);
            float impulseTarget = impulseStrength * tuning.impulseFlutterDeg() * tipFactor * impulseWave;
            float targetAngle = leanTarget + flutterTarget + rippleTarget + snapTarget + whipTarget + impulseTarget;
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

    public static float computeMotionStrength(float[] angles, float[] angularVelocities) {
        float velocityEnergy = 0.0f;
        for (float angularVelocity : angularVelocities) {
            velocityEnergy += Math.abs(angularVelocity);
        }

        float tipDeflection = Math.abs(angles[SEGMENTS - 1]) / 4.5f;
        float segmentShear = 0.0f;
        for (int i = 1; i < SEGMENTS; i++) {
            segmentShear += Math.abs(angles[i] - angles[i - 1]);
        }
        float averageVelocity = velocityEnergy / SEGMENTS;
        return Mth.clamp((averageVelocity / 0.26f) + tipDeflection + (segmentShear / 18.0f), 0.0f, 1.5f);
    }
}
