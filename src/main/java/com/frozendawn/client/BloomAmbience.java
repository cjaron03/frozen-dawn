package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Density-driven Bloom ambience; deliberately separate from Heart audio. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class BloomAmbience {
    private static TickableWindSound drone;
    private static int droneRestartTicks;
    private static int crackCooldown;
    private static int shimmerCooldown;

    private BloomAmbience() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        float density = BloomClientState.density();
        if (!PostMaeveClientState.isMaeveErased() || density <= 0.01F) {
            stop(minecraft);
            return;
        }

        if (drone != null && !drone.isStopped()) {
            drone.setTargetVolume(0.08F + density * 0.34F, 0.012F);
        }
        if (droneRestartTicks-- <= 0 || drone == null || drone.isStopped()) {
            drone = new TickableWindSound(
                    ModSounds.BLOOM_DRONE.get(), 0.02F,
                    0.96F + minecraft.level.random.nextFloat() * 0.06F, 440);
            drone.setTargetVolume(0.08F + density * 0.34F, 0.012F);
            minecraft.getSoundManager().play(drone);
            droneRestartTicks = 390;
        }

        if (crackCooldown-- <= 0) {
            BlockPos growth = findNearbyBloom(minecraft, 20, 10);
            if (growth != null) {
                minecraft.level.playLocalSound(
                        growth.getX() + 0.5D, growth.getY() + 0.5D,
                        growth.getZ() + 0.5D, ModSounds.BLOOM_CRACK.get(),
                        SoundSource.BLOCKS, 0.34F + density * 0.34F,
                        0.78F + minecraft.level.random.nextFloat() * 0.30F, false);
                spawnGrowthBeat(minecraft, growth, density);
            }
            crackCooldown = 220 + minecraft.level.random.nextInt(420);
        }
        if (BloomClientState.band() == BloomBand.FRONTIER && shimmerCooldown-- <= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_SHIMMER.get(),
                    0.95F + minecraft.level.random.nextFloat() * 0.12F,
                    0.08F + density * 0.22F));
            shimmerCooldown = 180 + minecraft.level.random.nextInt(360);
        }

        spawnTipParticles(minecraft, density);
        spawnDriftingMaterial(minecraft, density);
    }

    private static void spawnTipParticles(Minecraft minecraft, float density) {
        if (minecraft.level.random.nextFloat() > 0.08F + density * 0.18F) {
            return;
        }
        BlockPos pos = findNearbyBloom(minecraft, 10, 5, true);
        if (pos != null) {
            minecraft.level.addParticle(ParticleTypes.WAX_ON,
                    pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                    0.0D, 0.012D, 0.0D);
        }
    }

    private static void spawnDriftingMaterial(Minecraft minecraft, float density) {
        if (minecraft.level.random.nextFloat() > 0.10F + density * 0.22F) {
            return;
        }
        BlockPos source = findNearbyBloom(minecraft, 15, 5, false);
        if (source == null) {
            return;
        }
        int count = minecraft.level.random.nextFloat() < density * 0.55F ? 2 : 1;
        for (int index = 0; index < count; index++) {
            double angle = minecraft.level.random.nextDouble() * Math.PI * 2.0D;
            minecraft.level.addParticle(ModParticles.BLOOM_DRIFT.get(),
                    source.getX() + 0.5D + minecraft.level.random.nextGaussian() * 0.28D,
                    source.getY() + 0.35D + minecraft.level.random.nextDouble() * 0.7D,
                    source.getZ() + 0.5D + minecraft.level.random.nextGaussian() * 0.28D,
                    Math.cos(angle) * 0.003D,
                    0.002D + minecraft.level.random.nextDouble() * 0.004D,
                    Math.sin(angle) * 0.003D);
        }
    }

    private static void spawnGrowthBeat(Minecraft minecraft, BlockPos source,
                                        float density) {
        double x = source.getX() + 0.5D;
        double y = source.getY() + 0.55D;
        double z = source.getZ() + 0.5D;
        int tendrils = 3 + Math.round(density * 3.0F);
        for (int branch = 0; branch < tendrils; branch++) {
            double angle = Math.PI * 2.0D * branch / tendrils
                    + minecraft.level.random.nextDouble() * 0.6D;
            for (int step = 0; step < 7; step++) {
                double progress = step / 6.0D;
                double radius = progress * (0.8D + density * 0.9D);
                minecraft.level.addParticle(ModParticles.BLOOM_SPORE_ROOTING.get(),
                        x + Math.cos(angle) * radius,
                        y + Math.sin(progress * Math.PI) * 0.32D,
                        z + Math.sin(angle) * radius,
                        Math.cos(angle) * 0.006D, 0.008D,
                        Math.sin(angle) * 0.006D);
            }
        }
        BlockParticleOption fragment = new BlockParticleOption(
                ParticleTypes.BLOCK, minecraft.level.getBlockState(source));
        for (int index = 0; index < 9; index++) {
            minecraft.level.addParticle(fragment, x, y, z,
                    minecraft.level.random.nextGaussian() * 0.035D,
                    0.025D + minecraft.level.random.nextDouble() * 0.045D,
                    minecraft.level.random.nextGaussian() * 0.035D);
        }
    }

    private static BlockPos findNearbyBloom(Minecraft minecraft, int radius, int vertical) {
        return findNearbyBloom(minecraft, radius, vertical, false);
    }

    private static BlockPos findNearbyBloom(Minecraft minecraft, int radius, int vertical,
                                            boolean tipOnly) {
        BlockPos origin = minecraft.player.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = origin.getX()
                    + minecraft.level.random.nextIntBetweenInclusive(-radius, radius);
            int z = origin.getZ()
                    + minecraft.level.random.nextIntBetweenInclusive(-radius, radius);
            int surfaceY = minecraft.level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            for (int offset = 0; offset <= 8; offset++) {
                BlockPos candidate = new BlockPos(x, surfaceY - offset, z);
                if (matchesBloom(minecraft, candidate, tipOnly)) {
                    return candidate;
                }
            }
            int localY = origin.getY()
                    + minecraft.level.random.nextIntBetweenInclusive(-vertical, vertical);
            for (int offset = -2; offset <= 2; offset++) {
                BlockPos candidate = new BlockPos(x, localY + offset, z);
                if (matchesBloom(minecraft, candidate, tipOnly)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean matchesBloom(Minecraft minecraft, BlockPos pos,
                                        boolean tipOnly) {
        return tipOnly
                ? minecraft.level.getBlockState(pos).is(ModBlocks.BLOOM_TIP.get())
                : com.frozendawn.bloom.BloomGrowthManager.isBloomState(
                minecraft.level.getBlockState(pos));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop(Minecraft.getInstance());
        BloomClientState.reset();
    }

    private static void stop(Minecraft minecraft) {
        if (drone != null) {
            minecraft.getSoundManager().stop(drone);
            drone = null;
        }
        droneRestartTicks = 0;
        crackCooldown = 0;
        shimmerCooldown = 0;
    }
}
