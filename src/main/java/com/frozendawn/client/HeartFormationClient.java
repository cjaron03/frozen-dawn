package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Comparator;

/** Client-only audio, debris motion, and additive shake for Heart formation. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HeartFormationClient {
    private static FormationSound infrasound;
    private static FormationSound choir;
    private static ThaeIvenHeartEntity activeHeart;

    private HeartFormationClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            stopAll();
            return;
        }
        activeHeart = minecraft.level.getEntitiesOfClass(
                        ThaeIvenHeartEntity.class,
                        minecraft.player.getBoundingBox().inflate(144.0D),
                        entity -> entity.isAlive())
                .stream()
                .min(Comparator.comparingDouble(minecraft.player::distanceToSqr))
                .orElse(null);
        if (activeHeart == null) {
            fadeSounds(0.0F, 0.0F);
            return;
        }

        double distance = minecraft.player.distanceTo(activeHeart);
        float proximity = Mth.clamp((float) (1.0D - distance / 112.0D), 0.0F, 1.0F);
        HeartFormationStage stage = activeHeart.formationStage();
        float shakeVolume = stage == HeartFormationStage.SHAKE
                ? proximity * Mth.clamp(activeHeart.stageProgress() * 1.35F, 0.0F, 1.0F)
                : 0.0F;
        float gatherEnvelope = stage == HeartFormationStage.GATHER
                ? Math.min(Mth.clamp(activeHeart.stageProgress() / 0.18F, 0.0F, 1.0F),
                Mth.clamp((1.0F - activeHeart.stageProgress()) / 0.16F, 0.0F, 1.0F))
                : 0.0F;
        fadeSounds(shakeVolume * 0.62F, proximity * gatherEnvelope * 0.32F);

        if (stage == HeartFormationStage.SHAKE && proximity > 0.05F) {
            spawnSlidingDebris(minecraft, activeHeart, proximity);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (activeHeart == null
                || activeHeart.formationStage() != HeartFormationStage.SHAKE
                || !FrozenDawnConfig.ENABLE_FLOOD_SCREEN_EFFECTS.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        float proximity = Mth.clamp(
                1.0F - minecraft.player.distanceTo(activeHeart) / 112.0F, 0.0F, 1.0F);
        float strength = activeHeart.stageProgress() * proximity * 0.34F;
        double time = minecraft.level.getGameTime() * 0.57D + event.getPartialTick();
        event.setPitch(event.getPitch() + Mth.sin((float) time * 1.7F) * strength);
        event.setYaw(event.getYaw() + Mth.cos((float) time * 1.31F) * strength * 1.15F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        activeHeart = null;
        stopAll();
    }

    private static void spawnSlidingDebris(
            Minecraft minecraft, ThaeIvenHeartEntity heart, float proximity) {
        if (minecraft.level.random.nextFloat() > 0.28F + proximity * 0.42F) {
            return;
        }
        BlockPos anchor = BlockPos.of(heart.anchor());
        double angle = minecraft.level.random.nextDouble() * Math.PI * 2.0D;
        double radius = 5.0D + minecraft.level.random.nextDouble() * 22.0D;
        double x = anchor.getX() + 0.5D + Math.cos(angle) * radius;
        double z = anchor.getZ() + 0.5D + Math.sin(angle) * radius;
        double y = anchor.getY() + 0.35D;
        Vec3 inward = new Vec3(
                anchor.getX() + 0.5D - x, 0.0D, anchor.getZ() + 0.5D - z)
                .normalize().scale(0.08D + heart.stageProgress() * 0.12D);
        minecraft.level.addParticle(
                new BlockParticleOption(ParticleTypes.BLOCK,
                        minecraft.level.random.nextBoolean()
                                ? Blocks.PACKED_ICE.defaultBlockState()
                                : Blocks.ICE.defaultBlockState()),
                x, y, z, inward.x, 0.0D, inward.z);
    }

    private static void fadeSounds(float infrasoundVolume, float choirVolume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (infrasoundVolume > 0.001F && infrasound == null) {
            infrasound = new FormationSound(ModSounds.MASTER_ARCHITECT_INFRASOUND.get());
            minecraft.getSoundManager().play(infrasound);
        }
        if (choirVolume > 0.001F && choir == null) {
            choir = new FormationSound(ModSounds.THAE_IVEN_HEART_FORMATION.get());
            minecraft.getSoundManager().play(choir);
        }
        if (infrasound != null) {
            infrasound.setTarget(infrasoundVolume);
            if (infrasound.isStopped()) {
                infrasound = null;
            }
        }
        if (choir != null) {
            choir.setTarget(choirVolume);
            if (choir.isStopped()) {
                choir = null;
            }
        }
    }

    private static void stopAll() {
        if (infrasound != null) {
            infrasound.stopNow();
            infrasound = null;
        }
        if (choir != null) {
            choir.stopNow();
            choir = null;
        }
    }

    private static final class FormationSound extends AbstractTickableSoundInstance {
        private float target;

        private FormationSound(SoundEvent sound) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            relative = true;
            looping = true;
            attenuation = Attenuation.NONE;
            volume = 0.0F;
            pitch = 1.0F;
        }

        private void setTarget(float target) {
            this.target = Mth.clamp(target, 0.0F, 1.0F);
        }

        private void stopNow() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = Mth.approach(volume, target, target > volume ? 0.018F : 0.035F);
            if (target <= 0.0F && volume <= 0.001F) {
                stop();
            }
        }
    }
}
