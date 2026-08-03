package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
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
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_CRACK.get(),
                    0.8F + minecraft.level.random.nextFloat() * 0.35F,
                    0.12F + density * 0.34F));
            crackCooldown = 100 + minecraft.level.random.nextInt(260);
        }
        if (BloomClientState.band() == BloomBand.FRONTIER && shimmerCooldown-- <= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.BLOOM_SHIMMER.get(),
                    0.95F + minecraft.level.random.nextFloat() * 0.12F,
                    0.08F + density * 0.22F));
            shimmerCooldown = 180 + minecraft.level.random.nextInt(360);
        }

        spawnTipParticles(minecraft, density);
    }

    private static void spawnTipParticles(Minecraft minecraft, float density) {
        if (minecraft.level.random.nextFloat() > 0.08F + density * 0.18F) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        for (int attempt = 0; attempt < 5; attempt++) {
            BlockPos pos = origin.offset(
                    minecraft.level.random.nextIntBetweenInclusive(-10, 10),
                    minecraft.level.random.nextIntBetweenInclusive(-4, 5),
                    minecraft.level.random.nextIntBetweenInclusive(-10, 10));
            if (!minecraft.level.getBlockState(pos).is(ModBlocks.BLOOM_TIP.get())) {
                continue;
            }
            minecraft.level.addParticle(ParticleTypes.WAX_ON,
                    pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                    0.0D, 0.012D, 0.0D);
            break;
        }
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
