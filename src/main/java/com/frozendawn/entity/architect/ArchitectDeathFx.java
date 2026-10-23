package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

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

    /** Frost and ice flaking off the body as its joints lock. */
    public static void emitFelledCrumbs(ServerLevel serverLevel, double x, double y, double z) {
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                x, y + 1.2, z, 14, 0.22, 0.45, 0.22, 0.04);
        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 1.4, z, 6, 0.2, 0.3, 0.2, 0.01);
    }

    /** The fallen body breaks against the ground along its length. */
    public static void emitFelledImpact(ServerLevel serverLevel, double x, double y, double z,
                                        double dirX, double dirZ) {
        BlockState ground = serverLevel.getBlockState(BlockPos.containing(x, y - 0.5, z));
        BlockParticleOption groundDust = new BlockParticleOption(ParticleTypes.BLOCK,
                ground.isAir() ? Blocks.STONE.defaultBlockState() : ground);
        BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        for (int i = 0; i < 5; i++) {
            double along = 0.3 + i * 0.38;
            double px = x + dirX * along;
            double pz = z + dirZ * along;
            serverLevel.sendParticles(ice, px, y + 0.3, pz, 16, 0.25, 0.15, 0.25, 0.12);
            serverLevel.sendParticles(groundDust, px, y + 0.1, pz, 10, 0.3, 0.05, 0.3, 0.08);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, px, y + 0.25, pz, 2, 0.2, 0.05, 0.2, 0.02);
        }
    }

    /** The Scribe lands on its slate and its notes burst out from under it. */
    public static void emitScribePages(ServerLevel serverLevel, double x, double y, double z) {
        serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                x, y + 0.35, z, 22, 0.35, 0.1, 0.35, 0.16);
        serverLevel.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.5, z, 30, 0.6, 0.4, 0.6, 0.02);
    }

    private static final DustParticleOptions ASH = new DustParticleOptions(new Vector3f(0.04F, 0.04F, 0.05F), 1.3F);
    private static final BlockParticleOption ASH_FALL =
            new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());

    /** The slumped Blind sheds away as black dust, heaviest in the middle of the fade. */
    public static void emitWoundDownShed(ServerLevel serverLevel, double x, double y, double z, float progress) {
        int count = 2 + Math.round(6 * Mth.sin(Mth.clamp(progress, 0.0F, 1.0F) * Mth.PI));
        double top = y + 0.35 + 0.55 * (1.0 - progress);
        serverLevel.sendParticles(ASH, x, (y + top) * 0.5, z, count, 0.28, (top - y) * 0.35, 0.28, 0.0);
        serverLevel.sendParticles(ASH_FALL, x, top, z, count / 2 + 1, 0.3, 0.1, 0.3, 0.0);
        if (progress > 0.3F) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 1, 0.25, 0.1, 0.25, 0.005);
        }
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
