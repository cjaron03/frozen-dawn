package com.frozendawn.entity.architect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Death particle routines for Architect visual effects.
 */
public final class ArchitectDeathFx {

    private ArchitectDeathFx() {
    }

    public static void emitDeathSmokeBurst(ServerLevel serverLevel, RandomSource random, double x, double y, double z) {
        for (int i = 0; i < 18; i++) {
            emitDeathSmokeParticle(serverLevel, random, x, y, z, ParticleTypes.LARGE_SMOKE, 0.18, 0.34, 0.10, 0.28);
        }
        for (int i = 0; i < 34; i++) {
            emitDeathSmokeParticle(serverLevel, random, x, y, z, ParticleTypes.SMOKE, 0.24, 0.46, 0.06, 0.22);
        }
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 12, 0.12, 0.18, 0.12, 0.08);
    }

    public static void emitDeathSoulRise(ServerLevel serverLevel, int ticks, double x, double y, double z) {
        float progress = Mth.clamp((ticks - 21) / 9.0f, 0.0f, 1.0f);
        double baseY = y + 0.95 + progress * 1.55;
        double radius = 0.24 * (1.0 - progress * 0.55);
        double angleBase = ticks * 0.65;

        for (int i = 0; i < 2; i++) {
            double angle = angleBase + i * Math.PI;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            serverLevel.sendParticles(
                    ParticleTypes.SOUL,
                    px,
                    baseY + i * 0.08,
                    pz,
                    0,
                    Math.cos(angle) * 0.03,
                    0.16 + progress * 0.05,
                    Math.sin(angle) * 0.03,
                    1.0
            );
        }

        if (ticks % 2 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x,
                    baseY + 0.12,
                    z,
                    0,
                    Math.cos(angleBase + Math.PI * 0.5) * 0.018,
                    0.14 + progress * 0.04,
                    Math.sin(angleBase + Math.PI * 0.5) * 0.018,
                    1.0
            );
        }
    }

    public static void emitDeathSoulRelease(ServerLevel serverLevel, RandomSource random, double x, double y, double z) {
        double originY = y + 1.05;
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double horizontalSpeed = 0.03 + random.nextDouble() * 0.05;
            double verticalSpeed = 0.34 + random.nextDouble() * 0.16;
            serverLevel.sendParticles(
                    ParticleTypes.SOUL,
                    x,
                    originY,
                    z,
                    0,
                    Math.cos(angle) * horizontalSpeed,
                    verticalSpeed,
                    Math.sin(angle) * horizontalSpeed,
                    1.0
            );
        }
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * (Math.PI * 2.0);
            double horizontalSpeed = 0.015 + random.nextDouble() * 0.03;
            double verticalSpeed = 0.42 + random.nextDouble() * 0.18;
            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x,
                    originY + 0.1,
                    z,
                    0,
                    Math.cos(angle) * horizontalSpeed,
                    verticalSpeed,
                    Math.sin(angle) * horizontalSpeed,
                    1.0
            );
        }
    }

    private static void emitDeathSmokeParticle(
            ServerLevel serverLevel,
            RandomSource random,
            double x,
            double y,
            double z,
            ParticleOptions particleType,
            double horizontalMin,
            double horizontalMax,
            double verticalMin,
            double verticalMax
    ) {
        double angle = random.nextDouble() * (Math.PI * 2.0);
        double horizontalSpeed = horizontalMin + random.nextDouble() * (horizontalMax - horizontalMin);
        double verticalSpeed = verticalMin + random.nextDouble() * (verticalMax - verticalMin);
        double spawnY = y + 0.6 + random.nextDouble() * 0.9;
        serverLevel.sendParticles(
                particleType,
                x,
                spawnY,
                z,
                0,
                Math.cos(angle) * horizontalSpeed,
                verticalSpeed,
                Math.sin(angle) * horizontalSpeed,
                1.0
        );
    }
}
