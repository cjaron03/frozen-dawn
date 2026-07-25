package com.frozendawn.entity.master;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Master-only charge, detonation, and non-destructive blast effects. */
public final class MasterArchitectDeathFx {

    private MasterArchitectDeathFx() {
    }

    public static void tickCharge(
            ServerLevel level, ArchitectEntity architect, int deathTicks) {
        if (deathTicks < MasterArchitectCombatPolicy.DEATH_CHARGE_START_TICK) {
            return;
        }

        float charge = MasterArchitectCombatPolicy.deathChargeProgress(deathTicks);
        Vec3 center = architect.position().add(0.0D, 1.0D, 0.0D);
        int cadence = charge < 0.35F ? 3 : 1;
        if (deathTicks % cadence == 0) {
            int particles = charge < 0.55F ? 3 : 7;
            for (int i = 0; i < particles; i++) {
                double angle = architect.getRandom().nextDouble() * Math.PI * 2.0D;
                double radius = 0.8D + architect.getRandom().nextDouble() * 1.1D;
                double yOffset = architect.getRandom().nextDouble() * 1.9D - 0.7D;
                Vec3 origin = center.add(
                        Math.cos(angle) * radius,
                        yOffset,
                        Math.sin(angle) * radius);
                Vec3 velocity = center.subtract(origin).normalize()
                        .scale(0.07D + charge * 0.10D);
                level.sendParticles(
                        i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.END_ROD,
                        origin.x,
                        origin.y,
                        origin.z,
                        0,
                        velocity.x,
                        velocity.y,
                        velocity.z,
                        1.0D);
            }
        }
        if (deathTicks % 4 == 0) {
            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    center.x,
                    center.y,
                    center.z,
                    2 + Mth.floor(charge * 4.0F),
                    0.25D + charge * 0.35D,
                    0.55D + charge * 0.35D,
                    0.25D + charge * 0.35D,
                    0.03D + charge * 0.05D);
        }
        if (deathTicks % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    center.x,
                    center.y,
                    center.z,
                    4 + Mth.floor(charge * 9.0F),
                    0.45D + charge * 0.7D,
                    0.75D + charge * 0.55D,
                    0.45D + charge * 0.7D,
                    0.04D + charge * 0.09D);
        }
    }

    public static void detonate(ServerLevel level, ArchitectEntity architect) {
        Vec3 center = architect.position().add(0.0D, 1.0D, 0.0D);
        level.playSound(
                null,
                BlockPos.containing(center),
                SoundEvents.WITHER_DEATH,
                SoundSource.MASTER,
                6.5F,
                0.58F);
        if (!architect.isMindReturnDeathSoundOnly()) {
            level.playSound(
                    null,
                    BlockPos.containing(center),
                    ModSounds.MASTER_ARCHITECT_DETONATE.get(),
                    SoundSource.HOSTILE,
                    5.5F,
                    0.76F);
        }
        level.sendParticles(
                ParticleTypes.FLASH,
                center.x,
                center.y,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x,
                center.y,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                center.x,
                center.y,
                center.z,
                180,
                1.35D,
                1.7D,
                1.35D,
                0.55D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                center.x,
                center.y,
                center.z,
                80,
                0.9D,
                1.15D,
                0.9D,
                0.38D);
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                center.x,
                center.y,
                center.z,
                90,
                1.1D,
                1.35D,
                1.1D,
                0.44D);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                center.x,
                center.y,
                center.z,
                55,
                0.85D,
                1.05D,
                0.85D,
                0.25D);

        applyBlast(level, architect, center);
        HearthMasterArchitectWeatherManager.broadcastAuraEvent(
                level,
                MasterArchitectAuraEventPayload.DEATH_COLLAPSE,
                architect.blockPosition().above(128),
                architect.blockPosition(),
                2.0F);
        for (int strike = 0; strike < 3; strike++) {
            double angle = strike * Math.PI * 2.0D / 3.0D;
            double radius = strike == 0 ? 0.0D : 0.85D;
            MasterArchitectLightningEntity.spawn(
                    level,
                    architect.getX() + Math.cos(angle) * radius,
                    architect.getY(),
                    architect.getZ() + Math.sin(angle) * radius,
                    128.0F,
                    1.8F,
                    level.random.nextLong());
        }
    }

    private static void applyBlast(
            ServerLevel level, ArchitectEntity architect, Vec3 center) {
        double radius = MasterArchitectCombatPolicy.DEATH_BLAST_RADIUS;
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != architect && candidate.isAlive())) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            double distance = targetCenter.distanceTo(center);
            if (distance > radius) {
                continue;
            }
            float damage = MasterArchitectCombatPolicy.deathBlastDamage(distance);
            target.hurt(level.damageSources().explosion(null, architect), damage);

            Vec3 direction = targetCenter.subtract(center);
            if (direction.lengthSqr() < 1.0E-5D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            float falloff = 1.0F - Mth.clamp((float) (distance / radius), 0.0F, 1.0F);
            Vec3 push = direction.normalize().scale(0.35D + falloff * 0.75D);
            target.push(push.x, 0.25D + falloff * 0.30D, push.z);
            target.hurtMarked = true;
        }
    }
}
